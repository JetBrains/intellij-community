// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RenameJavaMethodProcessor extends RenameJavaMemberProcessor {
  private static final Logger LOG = Logger.getInstance(RenameJavaMethodProcessor.class);

  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof PsiMethod && !(element instanceof LightMethodBuilder);
  }

  @Override
  public void renameElement(@NotNull final PsiElement psiElement,
                            @NotNull final String newName,
                            final UsageInfo @NotNull [] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    PsiMethod method = (PsiMethod) psiElement;
    Set<PsiMethod> methodAndOverriders = new HashSet<>();
    Set<PsiClass> containingClasses = new HashSet<>();
    LinkedHashSet<PsiElement> renamedReferences = new LinkedHashSet<>();
    List<MemberHidesOuterMemberUsageInfo> outerHides = new ArrayList<>();
    List<MemberHidesStaticImportUsageInfo> staticImportHides = new ArrayList<>();

    methodAndOverriders.add(method);
    containingClasses.add(method.getContainingClass());

    // do actual rename of overriding/implementing methods and of references to all them
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;

      if (usage instanceof MemberHidesStaticImportUsageInfo) {
        staticImportHides.add((MemberHidesStaticImportUsageInfo)usage);
      } else if (usage instanceof MemberHidesOuterMemberUsageInfo) {
        PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
        PsiMethod resolved = (PsiMethod)collidingRef.resolve();
        outerHides.add(new MemberHidesOuterMemberUsageInfo(element, resolved));
      }
      else if (!(element instanceof PsiMethod)) {
        final PsiReference ref;
        if (usage instanceof MoveRenameUsageInfo) {
          ref = usage.getReference();
        }
        else {
          ref = element.getReference();
        }
        if (ref instanceof PsiImportStaticReferenceElement && ((PsiImportStaticReferenceElement)ref).multiResolve(false).length > 1) {
          continue;
        }
        if (ref != null) {
          PsiElement e = processRef(ref, newName);
          if (e != null) {
            renamedReferences.add(e);
          }
        }
      }
      else {
        PsiMethod overrider = (PsiMethod)element;
        methodAndOverriders.add(overrider);
        containingClasses.add(overrider.getContainingClass());
      }
    }

    // do actual rename of method
    method.setName(newName);
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element instanceof PsiMethod) {
        ((PsiMethod)element).setName(newName);
      }
    }
    if (listener != null) {
      listener.elementRenamed(method);
    }

    for (PsiElement element: renamedReferences) {
      fixNameCollisionsWithInnerClassMethod(element, newName, methodAndOverriders, containingClasses,
                                            method.hasModifierProperty(PsiModifier.STATIC));
    }
    qualifyOuterMemberReferences(outerHides);
    qualifyStaticImportReferences(staticImportHides);

    if (!method.isConstructor() && !(method instanceof LightElement) && method.findDeepestSuperMethods().length == 0) {
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, true, CommonClassNames.JAVA_LANG_OVERRIDE);
      if (annotation != null) {
        annotation.delete();
      }
    }
  }

  /**
   * handles rename of refs
   * @param ref
   * @param newName
   * @return
   */
  @Nullable
  protected PsiElement processRef(PsiReference ref, String newName) {
    return ref.handleElementRename(newName);
  }

  private static void fixNameCollisionsWithInnerClassMethod(final PsiElement element, final String newName,
                                                            final Set<PsiMethod> methodAndOverriders, final Set<PsiClass> containingClasses,
                                                            final boolean isStatic) throws IncorrectOperationException {
    if (!(element instanceof PsiReferenceExpression) || ((PsiReferenceExpression)element).getQualifierExpression() != null) return;
    PsiElement elem = ((PsiReferenceExpression)element).resolve();

    if (elem instanceof PsiMethod) {
      PsiMethod actualMethod = (PsiMethod) elem;
      if (actualMethod instanceof LightRecordMethod || actualMethod instanceof LightRecordCanonicalConstructor) return;
      if (!methodAndOverriders.contains(actualMethod)) {
        PsiClass outerClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        while (outerClass != null) {
          PsiClass finalOuterClass = outerClass;
          if (containingClasses.stream().anyMatch(psiClass -> InheritanceUtil.isInheritorOrSelf(finalOuterClass, psiClass, true))) {
            qualifyMember(element, newName, outerClass, isStatic);
            break;
          }
          outerClass = PsiTreeUtil.getParentOfType(outerClass, PsiClass.class);
        }
      }
    }
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(@NotNull PsiElement element,
                                                 @NotNull SearchScope searchScope,
                                                 boolean searchInCommentsAndStrings) {
    return MethodReferencesSearch.search((PsiMethod)element, searchScope, true).findAll();
  }

  @Override
  public void findCollisions(@NotNull final PsiElement element, @NotNull final String newName, @NotNull final Map<? extends PsiElement, String> allRenames,
                             @NotNull final List<UsageInfo> result) {
    final PsiMethod methodToRename = (PsiMethod)element;
    findSubmemberHidesMemberCollisions(methodToRename, newName, result);
    findMemberHidesOuterMemberCollisions((PsiMethod) element, newName, result);
    findCollisionsAgainstNewName(methodToRename, newName, result);
    findHidingMethodWithOtherSignature(methodToRename, newName, result);
    final PsiClass containingClass = methodToRename.getContainingClass();
    final PsiMethod patternMethod = getPrototypeWithNewName(methodToRename, newName);
    if (containingClass != null && patternMethod != null) {
      try {
        final PsiMethod methodInBaseClass = containingClass.findMethodBySignature(patternMethod, true);
        if (methodInBaseClass != null && methodInBaseClass.getContainingClass() != containingClass) {
          if (methodInBaseClass.hasModifierProperty(PsiModifier.FINAL)) {
            result.add(new UnresolvableCollisionUsageInfo(methodInBaseClass, methodToRename) {
              @Override
              public String getDescription() {
                return JavaRefactoringBundle
                  .message("renaming.method.will.override.final.0", RefactoringUIUtil.getDescription(methodInBaseClass, true));
              }
            });
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private void findHidingMethodWithOtherSignature(final PsiMethod methodToRename, final String newName, final List<UsageInfo> result) {
    final PsiClass containingClass = methodToRename.getContainingClass();
    if (containingClass != null) {
      final PsiMethod prototype = getPrototypeWithNewName(methodToRename, newName);
      if (prototype == null || containingClass.findMethodBySignature(prototype, true) != null) return;

      final PsiMethod[] methodsByName = containingClass.findMethodsByName(newName, true);
      if (methodsByName.length > 0) {

        for (UsageInfo info : result) {
          final PsiElement element = info.getElement();
          if (element instanceof PsiReferenceExpression) {
            if (((PsiReferenceExpression)element).resolve() == methodToRename) {
              final PsiElement parent = element.getParent();
              final PsiReferenceExpression copyRef;
              if (parent instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression copy = (PsiMethodCallExpression)JavaPsiFacade.getElementFactory(element.getProject())
                  .createExpressionFromText(parent.getText(), element);
                copyRef = copy.getMethodExpression();
              } else {
                LOG.assertTrue(element instanceof PsiMethodReferenceExpression, element.getText());
                copyRef = (PsiReferenceExpression)element.copy();
              }
              final PsiReferenceExpression expression = (PsiReferenceExpression)processRef(copyRef, newName);
              if (expression == null) continue;
              final JavaResolveResult resolveResult = expression.advancedResolve(true);
              final PsiMember resolveResultElement = (PsiMember)resolveResult.getElement();
              if (resolveResult.isValidResult() && resolveResultElement != null) {
                result.add(new UnresolvableCollisionUsageInfo(element, methodToRename) {
                  @Override
                  public String getDescription() {
                    return JavaRefactoringBundle.message("method.call.would.be.linked.to.0.after.rename",
                                                     RefactoringUIUtil.getDescription(resolveResultElement, true));
                  }
                });
                break;
              }
            }
          }
        }
      }
    }
  }

  private static PsiMethod getPrototypeWithNewName(PsiMethod methodToRename, String newName) {
    final PsiMethod prototype = (PsiMethod)methodToRename.copy();
    if (prototype != null) {
      try {
        prototype.setName(newName);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }
    return prototype;
  }

  @Override
  public void findExistingNameConflicts(@NotNull final PsiElement element, @NotNull final String newName, @NotNull final MultiMap<PsiElement, String> conflicts) {
    if (element instanceof PsiCompiledElement) return;
    final PsiMethod refactoredMethod = (PsiMethod)element;
    if (newName.equals(refactoredMethod.getName())) return;
    final PsiMethod prototype = getPrototypeWithNewName(refactoredMethod, newName);
    if (prototype == null) return;

    ConflictsUtil.checkMethodConflicts(
      refactoredMethod.getContainingClass(),
      refactoredMethod,
      prototype,
      conflicts);
  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull final String newName, @NotNull final Map<PsiElement, String> allRenames, @NotNull SearchScope scope) {
    final PsiMethod method = (PsiMethod) element;
    PsiMethod[] siblings = method.getUserData(SuperMethodWarningUtil.SIBLINGS);
    if (siblings == null) {
      siblings = new PsiMethod[] {method};
    }
    for (PsiMethod sibling : siblings) {
      //append all super methods
      if (sibling != method) {
        allRenames.put(sibling, newName);
      }

      OverridingMethodsSearch.search(sibling, scope, true).forEach(overrider -> {
        if (overrider instanceof PsiMirrorElement) {
          final PsiElement prototype = ((PsiMirrorElement)overrider).getPrototype();
          if (prototype instanceof PsiMethod) {
            overrider = (PsiMethod)prototype;
          }
        }

        if (overrider instanceof SyntheticElement) return true;

        final String overriderName = overrider.getName();
        final String baseName = sibling.getName();
        final String newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName);
        if (newOverriderName != null) {
          RenameUtil.assertNonCompileElement(overrider);
          allRenames.put(overrider, newOverriderName);
        }
        return true;
      });
    }
  }

  @Override
  @NonNls
  public String getHelpID(final PsiElement element) {
    return HelpID.RENAME_METHOD;
  }

  @Override
  public boolean isToSearchInComments(@NotNull final PsiElement psiElement) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
  }

  @Override
  public void setToSearchInComments(@NotNull final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = enabled;
  }

  @Override
  @Nullable
  public PsiElement substituteElementToRename(@NotNull PsiElement element, Editor editor) {
    PsiMethod psiMethod = (PsiMethod)element;
    if (psiMethod.isConstructor()) {
      PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass == null) return null;
      if (Comparing.strEqual(psiMethod.getName(), containingClass.getName())) {
        element = containingClass;
        if (!PsiElementRenameHandler.canRename(element.getProject(), editor, element)) {
          return null;
        }
        return element;
      }
    }
    PsiRecordComponent recordComponent = JavaPsiRecordUtil.getRecordComponentForAccessor(psiMethod);
    if (recordComponent != null) {
      return recordComponent;
    }
    return SuperMethodWarningUtil.checkSuperMethod(psiMethod);
  }

  @Override
  public void substituteElementToRename(@NotNull PsiElement element,
                                        @NotNull final Editor editor,
                                        @NotNull final Pass<PsiElement> renameCallback) {
    final PsiMethod psiMethod = (PsiMethod)element;
    if (psiMethod.isConstructor()) {
      final PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass == null) return;
      if (!Comparing.strEqual(psiMethod.getName(), containingClass.getName())) {
        renameCallback.pass(psiMethod);
        return;
      }
      super.substituteElementToRename(element, editor, renameCallback);
    }
    else {
      PsiRecordComponent recordComponent = JavaPsiRecordUtil.getRecordComponentForAccessor(psiMethod);
      if (recordComponent != null) {
        renameCallback.pass(recordComponent);
        return;
      }
      SuperMethodWarningUtil.checkSuperMethod(psiMethod, new PsiElementProcessor<>() {
        @Override
        public boolean execute(@NotNull PsiMethod method) {
          if (!PsiElementRenameHandler.canRename(method.getProject(), editor, method)) return false;
          renameCallback.pass(method);
          return false;
        }
      }, editor);
    }
  }

  private static void findSubmemberHidesMemberCollisions(final PsiMethod method, final String newName, final List<? super UsageInfo> result) {
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return;
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) return;
    Collection<PsiClass> inheritors = ClassInheritorsSearch.search(containingClass).findAll();

    MethodSignature oldSignature = method.getSignature(PsiSubstitutor.EMPTY);
    MethodSignature newSignature = MethodSignatureUtil.createMethodSignature(newName, oldSignature.getParameterTypes(),
                                                                             oldSignature.getTypeParameters(),
                                                                             oldSignature.getSubstitutor(),
                                                                             method.isConstructor());
    for (PsiClass inheritor : inheritors) {
      PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(containingClass, inheritor, PsiSubstitutor.EMPTY);
      final PsiMethod[] methodsByName = inheritor.findMethodsByName(newName, false);
      for (PsiMethod conflictingMethod : methodsByName) {
        if (newSignature.equals(conflictingMethod.getSignature(superSubstitutor))) {
          result.add(new SubmemberHidesMemberUsageInfo(conflictingMethod, method));
          break;
        }
      }
    }
  }

   @Override
   public boolean isToSearchForTextOccurrences(@NotNull final PsiElement element) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD;
  }

  @Override
  public void setToSearchForTextOccurrences(@NotNull final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD = enabled;
  }

  @NotNull
  @Override
  public UsageInfo createUsageInfo(@NotNull PsiElement element, @NotNull PsiReference ref, @NotNull PsiElement referenceElement) {
    return new MoveRenameUsageInfo(referenceElement, ref,
                                   ref.getRangeInElement().getStartOffset(),
                                   ref.getRangeInElement().getEndOffset(),
                                   element,
                                   ref.resolve() == null && !(ref instanceof PsiPolyVariantReference && ((PsiPolyVariantReference)ref).multiResolve(true).length > 0)) {
      @Override
      public boolean equals(Object o) {
        return super.equals(o) && o instanceof MoveRenameUsageInfo && element.equals(((MoveRenameUsageInfo)o).getReferencedElement());
      }

      @Override
      public int hashCode() {
        return 29 * super.hashCode() + element.hashCode();
      }
    };
  }
}
