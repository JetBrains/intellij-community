// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.GlobalUsageHelper;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.reference.PsiMemberReference;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.HashSet;

/**
 * Contains the information about references used locally in a given file.
 */
public final class LocalRefUseInfo {
  // resolved elements -> list of their references in this file
  private final @NotNull MultiMap<PsiElement, PsiReference> myLocalRefsMap;

  private final @NotNull Set<PsiAnchor> myDclsUsedMap;
  // used import statements
  private final @NotNull Set<PsiImportStatementBase> myUsedImports;

  /**
   * @param file Java/JSP file to get local reference use info for.
   * @return LocalRefUseInfo container (compute if it was not ready yet)
   */
  public static @NotNull LocalRefUseInfo forFile(@NotNull PsiFile file) {
    return CachedValuesManager.getCachedValue(file, () -> {
      Builder builder = new Builder(file);
      for (PsiFile psiFile : file.getViewProvider().getAllFiles()) {
        psiFile.accept(builder);
      }
      LocalRefUseInfo result = builder.build();
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  private LocalRefUseInfo(@NotNull MultiMap<PsiElement, PsiReference> myLocalRefsMap,
                          @NotNull Set<PsiAnchor> myDclsUsedMap,
                          @NotNull Set<PsiImportStatementBase> myImportStatements) {
    this.myLocalRefsMap = myLocalRefsMap;
    this.myDclsUsedMap = myDclsUsedMap;
    this.myUsedImports = myImportStatements;
  }

  public @NotNull GlobalUsageHelper getGlobalUsageHelper(@NotNull PsiFile file, @Nullable UnusedDeclarationInspectionBase deadCodeInspection) {
    FileViewProvider viewProvider = file.getViewProvider();
    Project project = file.getProject();

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile virtualFile = viewProvider.getVirtualFile();
    boolean inLibrary = fileIndex.isInLibrary(virtualFile);

    boolean isDeadCodeEnabled = deadCodeInspection != null && deadCodeInspection.isGlobalEnabledInEditor();
    if (isDeadCodeEnabled && !inLibrary) {
      return new GlobalUsageHelperBase() {
        final Map<PsiMember, Boolean> myEntryPointCache = FactoryMap.create((PsiMember member) -> {
          if (Registry.is("java.unused.symbol.strict.entry.points") ? deadCodeInspection.isStrictEntryPoint(member) : deadCodeInspection.isEntryPoint(member)) return true;
          if (member instanceof PsiClass) {
            return !JBTreeTraverser
              .<PsiMember>from(m -> m instanceof PsiClass
                                    ? JBIterable.from(PsiTreeUtil.getStubChildrenOfTypeAsList(m, PsiMember.class))
                                    : JBIterable.empty())
              .withRoot(member)
              .traverse()
              .skip(1)
              .processEach(this::shouldCheckUsages);
          }
          return false;
        });

        @Override
        public boolean shouldCheckUsages(@NotNull PsiMember member) {
          return !myEntryPointCache.get(member);
        }
      };
    }
    return new GlobalUsageHelperBase();
  }

  boolean isRedundant(@NotNull PsiImportStatementBase importStatement) {
    return !myUsedImports.contains(importStatement);
  }

  /**
   * @param variable to check
   * @param context scope (must be in the same file where variable is declared)
   * @return true if the variable is used within the context. Recursive use of parameter is still considered as a use in this method.
   */
  public boolean isVariableUsed(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    for (PsiReference reference : myLocalRefsMap.get(variable.getNavigationElement())) {
      if (PsiTreeUtil.isAncestor(context, reference.getElement(), false)) return true;
    }
    return false;
  }

  /**
   * @param variable to check
   * @param context scope (must be in the same file where variable is declared); null scope means the whole file 
   * @return list of reference expressions that refer the variable. Recursive use of parameter is still considered as a use in this method.
   */
  public List<PsiReferenceExpression> getVariableReferences(@NotNull PsiVariable variable, @Nullable PsiElement context) {
    Collection<PsiReference> references = myLocalRefsMap.get(variable.getNavigationElement());
    if (references.isEmpty()) return List.of();
    List<PsiReferenceExpression> result = new ArrayList<>();
    for (PsiReference reference : references) {
      if (reference.getElement() instanceof PsiReferenceExpression ref) {
        if (context == null || PsiTreeUtil.isAncestor(context, ref, false)) {
          result.add(ref);
        }
      }
    }
    return result;
  }

  /**
   * @param element element to check (variable, method, parameter, field, etc.)
   * @return true if the element is referenced in the same file
   */
  public boolean isReferenced(@NotNull PsiElement element) {
    Collection<PsiReference> array = myLocalRefsMap.get(element);
    if (!array.isEmpty() &&
        !isParameterUsedRecursively(element, array) &&
        !isClassUsedForInnerImports(element, array)) {
      for (PsiReference reference : array) {
        if (reference.isReferenceTo(element)) return true;
      }
    }

    return myDclsUsedMap.contains(PsiAnchor.create(element));
  }

  private boolean isClassUsedForInnerImports(@NotNull PsiElement element, @NotNull Collection<? extends PsiReference> array) {
    if (!(element instanceof PsiClass)) return false;

    Set<PsiImportStatementBase> imports = new HashSet<>();
    for (PsiReference classReference : array) {
      PsiImportStatementBase importStmt = PsiTreeUtil.getParentOfType(classReference.getElement(), PsiImportStatementBase.class);
      if (importStmt == null) return false;
      imports.add(importStmt);
    }

    return ContainerUtil.all(imports, importStmt -> {
      PsiElement importedMember = importStmt.resolve();
      if (importedMember != null && PsiTreeUtil.isAncestor(element, importedMember, false)) {
        for (PsiReference memberReference : myLocalRefsMap.get(importedMember)) {
          if (!PsiTreeUtil.isAncestor(element, memberReference.getElement(), false)) {
            return false;
          }
        }
        return true;
      }
      return false;
    });
  }

  private static boolean isParameterUsedRecursively(@NotNull PsiElement element, @NotNull Collection<? extends PsiReference> array) {
    if (!(element instanceof PsiParameter parameter)) return false;
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod method)) return false;
    int paramIndex = ArrayUtilRt.find(method.getParameterList().getParameters(), parameter);

    for (PsiReference reference : array) {
      if (!(reference instanceof PsiElement argument)) return false;

      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)new PsiMatcherImpl(argument)
        .dot(PsiMatchers.hasClass(PsiReferenceExpression.class))
        .parent(PsiMatchers.hasClass(PsiExpressionList.class))
        .parent(PsiMatchers.hasClass(PsiMethodCallExpression.class))
        .getElement();
      if (methodCallExpression == null) return false;
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      if (method != methodExpression.resolve()) return false;
      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      int argumentIndex = ArrayUtilRt.find(arguments, argument);
      if (paramIndex != argumentIndex) return false;
    }

    return true;
  }

  public boolean isReferencedForRead(@NotNull PsiVariable variable) {
    Collection<PsiReference> array = myLocalRefsMap.get(variable);
    if (array.isEmpty()) return false;
    for (PsiReference ref : array) {
      PsiElement refElement = ref.getElement();
      PsiElement resolved = ref.resolve();
      if (resolved != null) {
        ReadWriteAccessDetector.Access access = getAccess(ref, resolved);
        if (access != null && access.isReferencedForRead()) {
          if (isJustIncremented(access, refElement)) continue;
          return true;
        }
      }
    }
    return false;
  }

  private static ReadWriteAccessDetector.Access getAccess(@NotNull PsiReference ref, @NotNull PsiElement resolved) {
    PsiElement start = resolved.getLanguage() == ref.getElement().getLanguage() ? resolved : ref.getElement();
    ReadWriteAccessDetector detector = ReadWriteAccessDetector.findDetector(start);
    if (detector != null) {
      return detector.getReferenceAccess(resolved, ref);
    }
    return null;
  }

  // "var++;"
  private static boolean isJustIncremented(@NotNull ReadWriteAccessDetector.Access access, @NotNull PsiElement refElement) {
    return access == ReadWriteAccessDetector.Access.ReadWrite &&
           refElement instanceof PsiExpression &&
           refElement.getParent() instanceof PsiExpression &&
           refElement.getParent().getParent() instanceof PsiExpressionStatement;
  }

  public boolean isReferencedForWrite(@NotNull PsiVariable variable) {
    Collection<PsiReference> array = myLocalRefsMap.get(variable);
    if (array.isEmpty()) return false;
    for (PsiReference ref : array) {
      PsiElement resolved = ref.resolve();
      if (resolved != null) {
        ReadWriteAccessDetector.Access access = getAccess(ref, resolved);
        if (access != null && access.isReferencedForWrite()) {
          return true;
        }
      }
    }
    return false;
  }

  static @Nullable JavaResolveResult resolveOptimised(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiFile containingFile) {
    try {
      if (ref instanceof PsiReferenceExpressionImpl) {
        PsiReferenceExpressionImpl.OurGenericsResolver resolver = PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE;
        JavaResolveResult[] results = JavaResolveUtil.resolveWithContainingFile(ref, resolver, true, true, containingFile);
        return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
      }
      return ref.advancedResolve(true);
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  private static class Builder extends JavaRecursiveElementWalkingVisitor {
    private final @NotNull PsiFile myFile;
    private final @NotNull Set<PsiAnchor> myDclsUsedMap;
    private final @NotNull MultiMap<PsiElement, PsiReference> myLocalRefsMap;
    private final @NotNull Set<PsiImportStatementBase> myImportStatements;

    Builder(@NotNull PsiFile file) {
      myImportStatements = new HashSet<>();
      myDclsUsedMap = new HashSet<>();
      myFile = file;
      myLocalRefsMap = MultiMap.createLinkedSet();
    }

    LocalRefUseInfo build() {
      return new LocalRefUseInfo(myLocalRefsMap, myDclsUsedMap, myImportStatements);
    }

    @Override
    public void visitNameValuePair(@NotNull PsiNameValuePair pair) {
      super.visitNameValuePair(pair);
      PsiReference ref = pair.getReference();
      if (ref == null) return;
      PsiMethod method = (PsiMethod)ref.resolve();
      registerReference(ref, method != null ? new CandidateInfo(method, PsiSubstitutor.EMPTY) : JavaResolveResult.EMPTY);
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      if (aClass.getConstructors().length == 0) {
        registerSuperConstructor(aClass);
      }
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (method.isConstructor()) {
        if (JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method) == null) {
          registerSuperConstructor(method.getContainingClass());
        }
      }
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      super.visitElement(element);
      if(myFile instanceof ServerPageFile) {
        // in JSP, XmlAttributeValue may contain java references
        try {
          for (PsiReference reference : element.getReferences()) {
            if (reference instanceof PsiJavaReference psiJavaReference) {
              registerReference(reference, psiJavaReference.advancedResolve(false));
            }
          }
        }
        catch (IndexNotReadyException ignored) {
        }
      }
    }

    private void registerReferencesFromInjectedFragments(@NotNull PsiElement element) {
      InjectedLanguageManager manager = InjectedLanguageManager.getInstance(myFile.getProject());
      manager.enumerateEx(element, myFile, false, (injectedPsi, places) -> {
        if (InjectedLanguageJavaReferenceSupplier.containsPsiMemberReferences(injectedPsi.getLanguage().getID())) {
          injectedPsi.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
              super.visitElement(element);
              for (PsiReference reference : element.getReferences()) {
                PsiElement resolved = reference.resolve();
                if (resolved instanceof PsiNamedElement namedElement) {
                  registerLocallyReferenced(namedElement);
                  if (resolved instanceof PsiMember) {
                    registerReference(reference, new CandidateInfo(resolved, PsiSubstitutor.EMPTY));
                  }
                }
              }
            }
          });
        }
      });
    }

    private void registerSuperConstructor(@Nullable PsiClass aClass) {
      if (aClass == null) return;
      PsiClass baseClass = aClass.getSuperClass();
      if (baseClass == null) return;
      PsiMethod[] constructors = baseClass.getConstructors();
      if (constructors.length == 0) return;

      PsiElement resolved = JavaResolveUtil.resolveImaginarySuperCallInThisPlace(aClass, aClass.getProject(), baseClass);
      if (resolved instanceof PsiMethod constructor) {
        registerLocallyReferenced(constructor);
      }
    }

    private void registerLocallyReferenced(@NotNull PsiNamedElement result) {
      myDclsUsedMap.add(PsiAnchor.create(result));
    }

    void registerReference(@NotNull PsiReference ref, @NotNull JavaResolveResult resolveResult) {
      PsiElement refElement = resolveResult.getElement();
      PsiFile psiFile = refElement == null ? null : refElement.getContainingFile();
      if (psiFile != null) psiFile = (PsiFile)psiFile.getNavigationElement(); // look at navigation elements because all references resolve into Cls elements when highlighting library source
      if (refElement != null && psiFile != null && myFile.getViewProvider().equals(psiFile.getViewProvider())) {
        registerLocalRef(ref, refElement.getNavigationElement());
      }

      if (resolveResult.getCurrentFileResolveScope() instanceof PsiImportStatementBase importStatement) {
        registerImportStatement(importStatement);
      }
      else if (refElement == null && ref instanceof PsiJavaReference javaReference) {
        JavaResolveResult[] results = javaReference.multiResolve(true);
        if (results.length > 0) {
          for (JavaResolveResult result : results) {
            if (result.getCurrentFileResolveScope() instanceof PsiImportStatementBase importStatement) {
              registerImportStatement(importStatement);
              break;
            }
          }
        } else if (ref instanceof PsiJavaCodeReferenceElement javaRef) {
          for (PsiImportStatementBase potentialImport : IncompleteModelUtil.getPotentialImports(javaRef)) {
            registerImportStatement(potentialImport);
          }
        }
      }
      else if (ref instanceof PsiJavaCodeReferenceElement javaRef && refElement instanceof PsiVariable &&
               IncompleteModelUtil.canBeClassReference(javaRef)) {
        for (PsiImportStatementBase potentialImport : IncompleteModelUtil.getPotentialImports(javaRef)) {
          registerImportStatement(potentialImport);
        }
      }
    }

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      registerReferencesFromInjectedFragments(expression);
      for (PsiReference reference : expression.getReferences()) {
        if (reference instanceof PsiMemberReference) {
          PsiElement resolve = reference.resolve();
          if (resolve instanceof PsiMember) {
            registerReference(reference, new CandidateInfo(resolve, PsiSubstitutor.EMPTY));
          }
        }
      }
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      registerConstructorCall(expression);
    }

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
      super.visitReferenceElement(ref);
      if (!(ref instanceof PsiImportStaticReferenceElement)) {
        JavaResolveResult result = resolveOptimised(ref, myFile);
        if (result != null) {
          registerReference(ref, result);
        }
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
      super.visitReferenceExpression(ref);
      JavaResolveResult result = resolveOptimised(ref, myFile);
      if (result != null) {
        registerReference(ref, result);
      }
    }

    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      JavaResolveResult result = expression.advancedResolve(true);
      registerReference(expression, result);
      if (result.getElement() instanceof PsiMethod method) {
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
          registerLocallyReferenced(parameter);
        }
      }
    }

    @Override
    public void visitComment(@NotNull PsiComment comment) {
      super.visitComment(comment);
      registerReferencesFromInjectedFragments(comment);
    }

    @Override
    public void visitEnumConstant(@NotNull PsiEnumConstant enumConstant) {
      super.visitEnumConstant(enumConstant);
      registerConstructorCall(enumConstant);
    }

    private void registerConstructorCall(@NotNull PsiConstructorCall constructorCall) {
      if (constructorCall.resolveMethodGenerics().getElement() instanceof PsiNamedElement namedElement) {
        registerLocallyReferenced(namedElement);
      }
    }

    private void registerImportStatement(@NotNull PsiImportStatementBase importStatement) {
      myImportStatements.add(importStatement);
    }

    private void registerLocalRef(@NotNull PsiReference ref, PsiElement refElement) {
      PsiElement element = ref.getElement();
      if (refElement instanceof PsiMethod && PsiTreeUtil.isAncestor(refElement, element, true)) return; // filter self-recursive calls
      if (refElement instanceof PsiClass) {
        if (PsiTreeUtil.isAncestor(refElement, element, true)) {
          return; // filter inner use of itself
        }
      }
      myLocalRefsMap.putValue(refElement, ref);
    }
  }

  private class GlobalUsageHelperBase extends GlobalUsageHelper {
    @Override
    public boolean shouldCheckUsages(@NotNull PsiMember member) {
      return false;
    }

    @Override
    public boolean isCurrentFileAlreadyChecked() {
      return true;
    }

    @Override
    public boolean isLocallyUsed(@NotNull PsiNamedElement member) {
      return isReferenced(member);
    }
  }
}
