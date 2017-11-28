/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Checks and Highlights problems with classes
 * User: cdr
 * Date: Aug 19, 2002
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ClassUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class HighlightClassUtil {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  /**
   * new ref(...) or new ref(..) { ... } where ref is abstract class
   */
  @Nullable
  static HighlightInfo checkAbstractInstantiation(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    HighlightInfo highlightInfo = null;
    if (parent instanceof PsiAnonymousClass
             && parent.getParent() instanceof PsiNewExpression
             && !PsiUtilCore.hasErrorElementChild(parent.getParent())) {
      PsiAnonymousClass aClass = (PsiAnonymousClass)parent;
      highlightInfo = checkClassWithAbstractMethods(aClass, ref.getTextRange());
    }
    return highlightInfo;
  }

  @Nullable
  private static HighlightInfo checkClassWithAbstractMethods(PsiClass aClass, TextRange range) {
    return checkClassWithAbstractMethods(aClass, aClass, range);
  }

  @Nullable
  static HighlightInfo checkClassWithAbstractMethods(PsiClass aClass, PsiElement implementsFixElement, TextRange range) {
    PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(aClass);

    if (abstractMethod == null) {
      return null;
    }

    final PsiClass superClass = abstractMethod.getContainingClass();
    if (superClass == null) {
      return null;
    }

    String baseClassName = HighlightUtil.formatClass(aClass, false);
    String methodName = JavaHighlightUtil.formatMethod(abstractMethod);
    String message = JavaErrorMessages.message(aClass instanceof PsiEnumConstantInitializer || implementsFixElement instanceof PsiEnumConstant ? "enum.constant.should.implement.method" : "class.must.be.abstract",
                                               baseClassName,
                                               methodName,
                                               HighlightUtil.formatClass(superClass, false));

    HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message).create();
    final PsiMethod anyMethodToImplement = ClassUtil.getAnyMethodToImplement(aClass);
    if (anyMethodToImplement != null) {
      if (!anyMethodToImplement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) ||
          JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, superClass)) {
        QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createImplementMethodsFix(implementsFixElement));
      } else {
        QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(anyMethodToImplement, PsiModifier.PROTECTED, true, true));
        QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(anyMethodToImplement, PsiModifier.PUBLIC, true, true));
      }
    }
    if (!(aClass instanceof PsiAnonymousClass)
        && HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, aClass.getModifierList()) == null) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.ABSTRACT, true, false));
    }
    return errorResult;
  }

  @Nullable
  static HighlightInfo checkClassMustBeAbstract(final PsiClass aClass, final TextRange textRange) {
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) || aClass.getRBrace() == null || aClass.isEnum() && hasEnumConstants(aClass)) {
      return null;
    }
    return checkClassWithAbstractMethods(aClass, textRange);
  }

  @Nullable
  static HighlightInfo checkInstantiationOfAbstractClass(PsiClass aClass, @NotNull PsiElement highlightElement) {
    HighlightInfo errorResult = null;
    if (aClass != null && aClass.hasModifierProperty(PsiModifier.ABSTRACT)
        && (!(highlightElement instanceof PsiNewExpression) || !(((PsiNewExpression)highlightElement).getType() instanceof PsiArrayType))) {
      String baseClassName = aClass.getName();
      String message = JavaErrorMessages.message("abstract.cannot.be.instantiated", baseClassName);
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(highlightElement).descriptionAndTooltip(message).create();
      final PsiMethod anyAbstractMethod = ClassUtil.getAnyAbstractMethod(aClass);
      if (!aClass.isInterface() && anyAbstractMethod == null) {
        // suggest to make not abstract only if possible
        QuickFixAction.registerQuickFixAction(errorResult,
                                              QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.ABSTRACT, false, false));
      }
      if (anyAbstractMethod != null && highlightElement instanceof PsiNewExpression && ((PsiNewExpression)highlightElement).getClassReference() != null) {
        QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createImplementAbstractClassMethodsFix(highlightElement));
      }
    }
    return errorResult;
  }

  private static boolean hasEnumConstants(PsiClass aClass) {
    PsiField[] fields = aClass.getFields();
    for (PsiField field : fields) {
      if (field instanceof PsiEnumConstant) return true;
    }
    return false;
  }

  @Nullable
  static HighlightInfo checkDuplicateTopLevelClass(PsiClass aClass) {
    if (!(aClass.getParent() instanceof PsiFile)) return null;
    String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) return null;
    int numOfClassesToFind = 2;
    if (qualifiedName.contains("$")) {
      qualifiedName = qualifiedName.replaceAll("\\$", ".");
      numOfClassesToFind = 1;
    }
    PsiManager manager = aClass.getManager();
    Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
    if (module == null) return null;

    PsiClass[] classes = JavaPsiFacade.getInstance(aClass.getProject()).findClasses(qualifiedName, GlobalSearchScope.moduleScope(module).intersectWith(aClass.getResolveScope()));
    if (classes.length < numOfClassesToFind) return null;
    final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(aClass);
    if (virtualFile == null) return null;
    final boolean isTestSourceRoot = fileIndex.isInTestSourceContent(virtualFile);
    String dupFileName = null;
    for (PsiClass dupClass : classes) {
      // do not use equals
      if (dupClass != aClass) {
        VirtualFile file = dupClass.getContainingFile().getVirtualFile();
        if (file != null && manager.isInProject(dupClass) && fileIndex.isInTestSourceContent(file) == isTestSourceRoot) {
          dupFileName = FileUtil.toSystemDependentName(file.getPath());
          break;
        }
      }
    }
    if (dupFileName == null) return null;
    String message = JavaErrorMessages.message("duplicate.class.in.other.file", dupFileName);
    TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);

    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(message).create();
  }

  @Nullable
  static HighlightInfo checkDuplicateNestedClass(PsiClass aClass) {
    if (aClass == null) return null;
    PsiElement parent = aClass;
    if (aClass.getParent() instanceof PsiDeclarationStatement) {
      parent = aClass.getParent();
    }
    String name = aClass.getName();
    if (name == null) return null;
    boolean duplicateFound = false;
    boolean checkSiblings = true;
    while (parent != null) {
      if (parent instanceof PsiFile) break;
      PsiElement element = checkSiblings ? parent.getPrevSibling() : null;
      if (element == null) {
        element = parent.getParent();
        // JLS 14.3:
        // The name of a local class C may not be redeclared
        //  as a local class of the directly enclosing method, constructor, or initializer block within the scope of C
        // , or a compile-time error occurs.
        //  However, a local class declaration may be shadowed (?6.3.1)
        //  anywhere inside a class declaration nested within the local class declaration's scope.
        if (element instanceof PsiMethod || element instanceof PsiClass ||
            element instanceof PsiCodeBlock && element.getParent() instanceof PsiClassInitializer) {
          checkSiblings = false;
        }
      }
      parent = element;

      if (element instanceof PsiDeclarationStatement) element = PsiTreeUtil.getChildOfType(element, PsiClass.class);
      if (element instanceof PsiClass && name.equals(((PsiClass)element).getName())) {
        duplicateFound = true;
        break;
      }
    }

    if (duplicateFound) {
      String message = JavaErrorMessages.message("duplicate.class", name);
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(message).create();
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkPublicClassInRightFile(PsiClass aClass) {
    PsiFile containingFile = aClass.getContainingFile();
    if (aClass.getParent() != containingFile || !aClass.hasModifierProperty(PsiModifier.PUBLIC) || !(containingFile instanceof PsiJavaFile)) return null;
    PsiJavaFile file = (PsiJavaFile)containingFile;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null || virtualFile.getNameWithoutExtension().equals(aClass.getName())) {
      return null;
    }
    String message = JavaErrorMessages.message("public.class.should.be.named.after.file", aClass.getName());
    TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).
      range(aClass, range.getStartOffset(), range.getEndOffset()).
      descriptionAndTooltip(message).create();
    PsiModifierList psiModifierList = aClass.getModifierList();
    QuickFixAction.registerQuickFixAction(errorResult,
                                          QUICK_FIX_FACTORY.createModifierListFix(psiModifierList, PsiModifier.PUBLIC, false, false));
    PsiClass[] classes = file.getClasses();
    if (classes.length > 1) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createMoveClassToSeparateFileFix(aClass));
    }
    for (PsiClass otherClass : classes) {
      if (!otherClass.getManager().areElementsEquivalent(otherClass, aClass) &&
          otherClass.hasModifierProperty(PsiModifier.PUBLIC) &&
          virtualFile.getNameWithoutExtension().equals(otherClass.getName())) {
        return errorResult;
      }
    }
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createRenameFileFix(aClass.getName() + JavaFileType.DOT_DEFAULT_EXTENSION));
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createRenameElementFix(aClass));
    return errorResult;
  }

  static HighlightInfo checkVarClassConflict(PsiClass psiClass, PsiIdentifier identifier) {
    String className = psiClass.getName();
    if (className != null && PsiKeyword.VAR.equals(className)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .descriptionAndTooltip("'var' is a restricted local variable type and cannot be used for type declarations")
        .range(identifier)
        .create();
    }
    return null;
  }
  
  @Nullable
  static HighlightInfo checkClassAndPackageConflict(@NotNull PsiClass aClass) {
    String name = aClass.getQualifiedName();

    if (CommonClassNames.DEFAULT_PACKAGE.equals(name)) {
      String message = JavaErrorMessages.message("class.clashes.with.package", name);
      TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message).create();
    }

    PsiElement file = aClass.getParent();
    if (file instanceof PsiJavaFile && !((PsiJavaFile)file).getPackageName().isEmpty()) {
      PsiElement directory = file.getParent();
      if (directory instanceof PsiDirectory) {
        String simpleName = aClass.getName();
        PsiDirectory subDirectory = ((PsiDirectory)directory).findSubdirectory(simpleName);
        if (subDirectory != null && simpleName.equals(subDirectory.getName()) && PsiTreeUtil.findChildOfType(subDirectory, PsiJavaFile.class) != null) {
          String message = JavaErrorMessages.message("class.clashes.with.package", name);
          TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message).create();
        }
      }
    }

    return null;
  }

  @Nullable
  private static HighlightInfo checkStaticFieldDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiField.class) == null) {
      return null;
    }

    PsiField field = (PsiField)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(field) || PsiUtil.isCompileTimeConstant((PsiVariable)field)) {
      return null;
    }

    String message = JavaErrorMessages.message("static.declaration.in.inner.class");
    HighlightInfo result = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(message).create();

    QuickFixAction.registerQuickFixAction(result, QUICK_FIX_FACTORY.createModifierListFix(field, PsiModifier.STATIC, false, false));
    registerMakeInnerClassStatic(result, field.getContainingClass());

    return result;
  }

  private static void registerMakeInnerClassStatic(HighlightInfo result, PsiClass aClass) {
    if (aClass != null && aClass.getContainingClass() != null) {
      QuickFixAction.registerQuickFixAction(result, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, true, false));
    }
  }

  @Nullable
  private static HighlightInfo checkStaticMethodDeclarationInInnerClass(PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiMethod.class) == null) {
      return null;
    }
    PsiMethod method = (PsiMethod)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(method)) return null;
    String message = JavaErrorMessages.message("static.declaration.in.inner.class");
    HighlightInfo result = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(message).create();
    QuickFixAction.registerQuickFixAction(result, QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.STATIC, false, false));
    registerMakeInnerClassStatic(result, (PsiClass)keyword.getParent().getParent().getParent());
    return result;
  }

  @Nullable
  private static HighlightInfo checkStaticInitializerDeclarationInInnerClass(PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiClassInitializer.class) == null) {
      return null;
    }
    PsiClassInitializer initializer = (PsiClassInitializer)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(initializer)) return null;
    String message = JavaErrorMessages.message("static.declaration.in.inner.class");
    HighlightInfo result = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(message).create();
    QuickFixAction.registerQuickFixAction(result, QUICK_FIX_FACTORY.createModifierListFix(initializer, PsiModifier.STATIC, false, false));
    registerMakeInnerClassStatic(result, (PsiClass)keyword.getParent().getParent().getParent());
    return result;
  }

  private static PsiElement getEnclosingStaticClass(@NotNull PsiKeyword keyword, @NotNull Class<?> parentClass) {
    return new PsiMatcherImpl(keyword)
      .dot(PsiMatchers.hasText(PsiModifier.STATIC))
      .parent(PsiMatchers.hasClass(PsiModifierList.class))
      .parent(PsiMatchers.hasClass(parentClass))
      .parent(PsiMatchers.hasClass(PsiClass.class))
      .dot(JavaMatchers.hasModifier(PsiModifier.STATIC, false))
      .parent(PsiMatchers.hasClass(PsiClass.class, PsiDeclarationStatement.class, PsiNewExpression.class, PsiEnumConstant.class))
      .getElement();
  }

  @Nullable
  private static HighlightInfo checkStaticClassDeclarationInInnerClass(PsiKeyword keyword) {
    // keyword points to 'class' or 'interface' or 'enum'
    if (new PsiMatcherImpl(keyword)
          .parent(PsiMatchers.hasClass(PsiClass.class))
          .dot(JavaMatchers.hasModifier(PsiModifier.STATIC, true))
          .parent(PsiMatchers.hasClass(PsiClass.class))
          .dot(JavaMatchers.hasModifier(PsiModifier.STATIC, false))
          .parent(PsiMatchers.hasClass(PsiClass.class, PsiDeclarationStatement.class, PsiNewExpression.class, PsiEnumConstant.class))
          .getElement() == null) {
      return null;
    }

    PsiClass aClass = (PsiClass)keyword.getParent();
    if (PsiUtilCore.hasErrorElementChild(aClass)) {
      return null;
    }

    // highlight 'static' keyword if any, or class or interface if not
    PsiElement context = null;
    PsiModifierList modifierList = aClass.getModifierList();
    if (modifierList != null) {
      for (PsiElement element : modifierList.getChildren()) {
        if (Comparing.equal(element.getText(), PsiModifier.STATIC)) {
          context = element;
          break;
        }
      }
    }
    TextRange range = context != null ? context.getTextRange() : HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    String message = JavaErrorMessages.message("static.declaration.in.inner.class");
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message).create();
    if (context != keyword) {
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, false, false));
    }
    PsiClass containingClass = aClass.getContainingClass();
    registerMakeInnerClassStatic(info, containingClass);
    return info;
  }

  @Nullable
  static HighlightInfo checkStaticDeclarationInInnerClass(PsiKeyword keyword) {
    HighlightInfo errorResult = checkStaticFieldDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    errorResult = checkStaticMethodDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    errorResult = checkStaticClassDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    errorResult = checkStaticInitializerDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    return null;
  }

  @Nullable
  static HighlightInfo checkExtendsAllowed(PsiReferenceList list) {
    if (list.getParent() instanceof PsiClass) {
      PsiClass aClass = (PsiClass)list.getParent();
      if (aClass.isEnum()) {
        boolean isExtends = list.equals(aClass.getExtendsList());
        if (isExtends) {
          String description = JavaErrorMessages.message("extends.after.enum");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).create();
        }
      }
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkImplementsAllowed(PsiReferenceList list) {
    if (list.getParent() instanceof PsiClass) {
      PsiClass aClass = (PsiClass)list.getParent();
      if (aClass.isInterface()) {
        boolean isImplements = list.equals(aClass.getImplementsList());
        if (isImplements) {
          String description = JavaErrorMessages.message("implements.after.interface");
          HighlightInfo result =
            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).create();
          final PsiClassType[] referencedTypes = list.getReferencedTypes();
          if (referencedTypes.length > 0) {
            QuickFixAction.registerQuickFixAction(result, QUICK_FIX_FACTORY.createChangeExtendsToImplementsFix(aClass, referencedTypes[0]));
          }
          return result;
        }
      }
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkExtendsClassAndImplementsInterface(PsiReferenceList referenceList,
                                                               JavaResolveResult resolveResult,
                                                               PsiJavaCodeReferenceElement ref) {
    PsiClass aClass = (PsiClass)referenceList.getParent();
    boolean isImplements = referenceList.equals(aClass.getImplementsList());
    boolean isInterface = aClass.isInterface();
    if (isInterface && isImplements) return null;
    boolean mustBeInterface = isImplements || isInterface;
    HighlightInfo errorResult = null;
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (extendFrom.isInterface() != mustBeInterface) {
      String message = JavaErrorMessages.message(mustBeInterface ? "interface.expected" : "no.interface.expected");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(message).create();
      PsiClassType type =
        JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(ref);
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createChangeExtendsToImplementsFix(aClass, type));
    }
    return errorResult;
  }

  @Nullable
  static HighlightInfo checkCannotInheritFromFinal(PsiClass superClass, PsiElement elementToHighlight) {
    HighlightInfo errorResult = null;
    if (superClass.hasModifierProperty(PsiModifier.FINAL) || superClass.isEnum()) {
      String message = JavaErrorMessages.message("inheritance.from.final.class", superClass.getQualifiedName());
      errorResult =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(errorResult,
                                            QUICK_FIX_FACTORY.createModifierListFix(superClass, PsiModifier.FINAL, false, false));
    }
    return errorResult;
  }

  @Nullable
  static HighlightInfo checkAnonymousInheritFinal(PsiNewExpression expression) {
    PsiAnonymousClass aClass = PsiTreeUtil.getChildOfType(expression, PsiAnonymousClass.class);
    if (aClass == null) return null;
    PsiClassType baseClassReference = aClass.getBaseClassType();
    PsiClass baseClass = baseClassReference.resolve();
    if (baseClass == null) return null;
    return checkCannotInheritFromFinal(baseClass, aClass.getBaseClassReference());
  }

  @Nullable
  private static String checkDefaultConstructorThrowsException(PsiMethod constructor, @NotNull PsiClassType[] handledExceptions) {
    PsiClassType[] referencedTypes = constructor.getThrowsList().getReferencedTypes();
    List<PsiClassType> exceptions = new ArrayList<>();
    for (PsiClassType referencedType : referencedTypes) {
      if (!ExceptionUtil.isUncheckedException(referencedType) && !ExceptionUtil.isHandledBy(referencedType, handledExceptions)) {
        exceptions.add(referencedType);
      }
    }
    if (!exceptions.isEmpty()) {
      return HighlightUtil.getUnhandledExceptionsDescriptor(exceptions);
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkClassDoesNotCallSuperConstructorOrHandleExceptions(@NotNull PsiClass aClass,
                                                                               RefCountHolder refCountHolder,
                                                                               @NotNull PsiResolveHelper resolveHelper) {
    if (aClass.isEnum()) return null;
    // check only no-ctr classes. Problem with specific constructor will be highlighted inside it
    if (aClass.getConstructors().length != 0) return null;
    // find no-args base class ctr
    TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    return checkBaseClassDefaultConstructorProblem(aClass, refCountHolder, resolveHelper, textRange, PsiClassType.EMPTY_ARRAY);
  }

  static HighlightInfo checkBaseClassDefaultConstructorProblem(@NotNull PsiClass aClass,
                                                               RefCountHolder refCountHolder,
                                                               @NotNull PsiResolveHelper resolveHelper,
                                                               @NotNull TextRange range,
                                                               @NotNull PsiClassType[] handledExceptions) {
    if (aClass instanceof PsiAnonymousClass) return null;
    PsiClass baseClass = aClass.getSuperClass();
    if (baseClass == null) return null;
    PsiMethod[] constructors = baseClass.getConstructors();
    if (constructors.length == 0) return null;

    PsiElement resolved = JavaResolveUtil.resolveImaginarySuperCallInThisPlace(aClass, aClass.getProject(), baseClass);
    List<PsiMethod> constructorCandidates = (resolved != null ? Collections.singletonList((PsiMethod)resolved)
                                                              : Arrays.asList(constructors))
      .stream()
      .filter(constructor -> {
        PsiParameter[] parameters = constructor.getParameterList().getParameters();
        return (parameters.length == 0 || parameters.length == 1 && parameters[0].isVarArgs()) &&
               resolveHelper.isAccessible(constructor, aClass, null);
      })
      .limit(2).collect(Collectors.toList());

    if (constructorCandidates.size() >= 2) {// two ambiguous var-args-only constructors
      final String m1 = PsiFormatUtil.formatMethod(constructorCandidates.get(0), PsiSubstitutor.EMPTY,
                                                   PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                   PsiFormatUtilBase.SHOW_NAME |
                                                   PsiFormatUtilBase.SHOW_PARAMETERS,
                                                   PsiFormatUtilBase.SHOW_TYPE);
      final String m2 = PsiFormatUtil.formatMethod(constructorCandidates.get(1), PsiSubstitutor.EMPTY,
                                                   PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                   PsiFormatUtilBase.SHOW_NAME |
                                                   PsiFormatUtilBase.SHOW_PARAMETERS,
                                                   PsiFormatUtilBase.SHOW_TYPE);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(range)
        .descriptionAndTooltip(JavaErrorMessages.message("ambiguous.method.call", m1, m2))
        .create();
    }

    if (!constructorCandidates.isEmpty()) {
      PsiMethod constructor = constructorCandidates.get(0);
      String description = checkDefaultConstructorThrowsException(constructor, handledExceptions);
      if (description != null) {
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description).create();
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createCreateConstructorMatchingSuperFix(aClass));
        return info;
      }
      if (refCountHolder != null) {
        refCountHolder.registerLocallyReferenced(constructor);
      }
      return null;
    }

    String description = JavaErrorMessages.message("no.default.constructor.available", HighlightUtil.formatClass(baseClass));

    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description).create();
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createCreateConstructorMatchingSuperFix(aClass));

    return info;
  }

  @Nullable
  static HighlightInfo checkInterfaceCannotBeLocal(PsiClass aClass) {
    if (PsiUtil.isLocalClass(aClass)) {
      TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      String description = JavaErrorMessages.message("interface.cannot.be.local");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description).create();
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkCyclicInheritance(PsiClass aClass) {
    PsiClass circularClass = getCircularClass(aClass, new HashSet<>());
    if (circularClass != null) {
      String description = JavaErrorMessages.message("cyclic.inheritance", HighlightUtil.formatClass(circularClass));
      TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description).create();
    }
    return null;
  }

  @Nullable
  public static PsiClass getCircularClass(PsiClass aClass, Collection<PsiClass> usedClasses) {
    if (usedClasses.contains(aClass)) {
      return aClass;
    }
    try {
      usedClasses.add(aClass);
      PsiClass[] superTypes = aClass.getSupers();
      for (PsiElement superType : superTypes) {
        while (superType instanceof PsiClass) {
          if (!CommonClassNames.JAVA_LANG_OBJECT.equals(((PsiClass)superType).getQualifiedName())) {
            PsiClass circularClass = getCircularClass((PsiClass)superType, usedClasses);
            if (circularClass != null) return circularClass;
          }
          // check class qualifier
          superType = superType.getParent();
        }
      }
    }
    finally {
      usedClasses.remove(aClass);
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkExtendsDuplicate(PsiJavaCodeReferenceElement element, PsiElement resolved, @NotNull PsiFile containingFile) {
    if (!(element.getParent() instanceof PsiReferenceList)) return null;
    PsiReferenceList list = (PsiReferenceList)element.getParent();
    if (!(list.getParent() instanceof PsiClass)) return null;
    if (!(resolved instanceof PsiClass)) return null;
    PsiClass aClass = (PsiClass)resolved;
    PsiClassType[] referencedTypes = list.getReferencedTypes();
    int dupCount = 0;
    PsiManager manager = containingFile.getManager();
    for (PsiClassType referencedType : referencedTypes) {
      PsiClass resolvedElement = referencedType.resolve();
      if (resolvedElement != null && manager.areElementsEquivalent(resolvedElement, aClass)) {
        dupCount++;
      }
    }
    if (dupCount > 1) {
      String description = JavaErrorMessages.message("duplicate.class", HighlightUtil.formatClass(aClass));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkClassAlreadyImported(PsiClass aClass, PsiElement elementToHighlight) {
    PsiFile file = aClass.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return null;
    PsiJavaFile javaFile = (PsiJavaFile)file;
    // check only top-level classes conflicts
    if (aClass.getParent() != javaFile) return null;
    PsiImportList importList = javaFile.getImportList();
    if (importList == null) return null;
    PsiImportStatementBase[] importStatements = importList.getAllImportStatements();
    for (PsiImportStatementBase importStatement : importStatements) {
      if (importStatement.isOnDemand()) continue;
      PsiElement resolved = importStatement.resolve();
      if (resolved instanceof PsiClass && !resolved.equals(aClass) && Comparing.equal(aClass.getName(), ((PsiClass)resolved).getName(), true)) {
        String description = JavaErrorMessages.message("class.already.imported", HighlightUtil.formatClass(aClass, false));
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description).create();
      }
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkClassExtendsOnlyOneClass(PsiReferenceList list) {
    PsiClassType[] referencedTypes = list.getReferencedTypes();
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiClass)) return null;

    PsiClass aClass = (PsiClass)parent;
    if (!aClass.isInterface()
        && referencedTypes.length > 1
        && aClass.getExtendsList() == list) {
      String description = JavaErrorMessages.message("class.cannot.extend.multiple.classes");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).create();
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkThingNotAllowedInInterface(PsiElement element, PsiClass aClass) {
    if (aClass == null || !aClass.isInterface()) return null;
    String description = JavaErrorMessages.message("not.allowed.in.interface");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
  }

  @Nullable
  static HighlightInfo checkQualifiedNew(PsiNewExpression expression, PsiType type, PsiClass aClass) {
    PsiExpression qualifier = expression.getQualifier();
    if (qualifier == null) return null;
    if (type instanceof PsiArrayType) {
      String description = JavaErrorMessages.message("invalid.qualified.new");
      HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createRemoveNewQualifierFix(expression, null));
      return info;
    }
    HighlightInfo info = null;
    if (aClass != null) {
      if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
        String description = JavaErrorMessages.message("qualified.new.of.static.class");
        info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
        if (!aClass.isEnum()) {
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, false, false));
        }

      } else if (aClass instanceof PsiAnonymousClass) {
        final PsiClass baseClass = PsiUtil.resolveClassInType(((PsiAnonymousClass)aClass).getBaseClassType());
        if (baseClass != null && baseClass.isInterface()) {
          info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
            .descriptionAndTooltip("Anonymous class implements interface; cannot have qualifier for new").create();
        }
      }
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createRemoveNewQualifierFix(expression, aClass));
    }
    return info;
  }


  /**
   * class c extends foreign.inner {}
   *
   * @param extendRef points to the class in the extends list
   * @param resolved  extendRef resolved
   */
  @Nullable
  static HighlightInfo checkClassExtendsForeignInnerClass(final PsiJavaCodeReferenceElement extendRef, final PsiElement resolved) {
    PsiElement parent = extendRef.getParent();
    if (!(parent instanceof PsiReferenceList)) {
      return null;
    }
    PsiElement grand = parent.getParent();
    if (!(grand instanceof PsiClass)) {
      return null;
    }
    final PsiClass aClass = (PsiClass)grand;
    final PsiClass containerClass;
    if (aClass instanceof PsiTypeParameter) {
      final PsiTypeParameterListOwner owner = ((PsiTypeParameter)aClass).getOwner();
      if (!(owner instanceof PsiClass)) {
        return null;
      }
      containerClass = (PsiClass)owner;
    } else {
      containerClass = aClass;
    }
    if (aClass.getExtendsList() != parent && aClass.getImplementsList() != parent) {
      return null;
    }
    if (!(resolved instanceof PsiClass)) {
      String description = JavaErrorMessages.message("class.name.expected");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(extendRef).descriptionAndTooltip(description).create();
    }
    final HighlightInfo[] infos = new HighlightInfo[1];
    extendRef.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (infos[0] != null) return;
        super.visitElement(element);
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiClass) {
          final PsiClass base = (PsiClass)resolve;
          final PsiClass baseClass = base.getContainingClass();
          if (baseClass != null && base.hasModifierProperty(PsiModifier.PRIVATE) && baseClass == containerClass) {
            String description = JavaErrorMessages.message("private.symbol",
                                                           HighlightUtil.formatClass(base),
                                                           HighlightUtil.formatClass(baseClass));
            infos[0] = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(extendRef).descriptionAndTooltip(description).create();
            return;
          }

          // must be inner class
          if (!PsiUtil.isInnerClass(base)) return;

          if (resolve == resolved && baseClass != null && (!PsiTreeUtil.isAncestor(baseClass, extendRef, true) || aClass.hasModifierProperty(PsiModifier.STATIC)) &&
              !InheritanceUtil.hasEnclosingInstanceInScope(baseClass, extendRef, psiClass -> psiClass != aClass, true) &&
              !qualifiedNewCalledInConstructors(aClass)) {
            String description = JavaErrorMessages.message("no.enclosing.instance.in.scope", HighlightUtil.formatClass(baseClass));
            infos[0] = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(extendRef).descriptionAndTooltip(description).create();
          }
        }
      }
    });

    return infos[0];
  }

  /**
   * 15.9 Class Instance Creation Expressions | 15.9.2 Determining Enclosing Instances
   */
  private static boolean qualifiedNewCalledInConstructors(final PsiClass aClass) {
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) return false;
    for (PsiMethod constructor : constructors) {
      PsiCodeBlock body = constructor.getBody();
      if (body == null) return false;
      PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) return false;
      PsiStatement firstStatement = statements[0];
      if (!(firstStatement instanceof PsiExpressionStatement)) return false;
      PsiExpression expression = ((PsiExpressionStatement)firstStatement).getExpression();
      if (!RefactoringChangeUtil.isSuperOrThisMethodCall(expression)) return false;
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      if (PsiKeyword.THIS.equals(methodCallExpression.getMethodExpression().getReferenceName())) continue;
      PsiReferenceExpression referenceExpression = methodCallExpression.getMethodExpression();
      PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(referenceExpression.getQualifierExpression());
      //If the class instance creation expression is qualified, then the immediately
      //enclosing instance of i is the object that is the value of the Primary expression or the ExpressionName,
      //otherwise aClass needs to be a member of a class enclosing the class in which the class instance creation expression appears
      //already excluded by InheritanceUtil.hasEnclosingInstanceInScope
      if (qualifierExpression == null) return false;
    }
    return true;
  }

  @Nullable
  static HighlightInfo checkCreateInnerClassFromStaticContext(PsiNewExpression expression, PsiType type, PsiClass aClass) {
    if (type == null || type instanceof PsiArrayType || type instanceof PsiPrimitiveType) return null;
    if (aClass == null) return null;
    if (aClass instanceof PsiAnonymousClass) {
      aClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
      if (aClass == null) return null;
    }

    PsiExpression qualifier = expression.getQualifier();
    return checkCreateInnerClassFromStaticContext(expression, qualifier, aClass);
  }

  @Nullable
  public static HighlightInfo checkCreateInnerClassFromStaticContext(PsiElement element,
                                                                     @Nullable PsiExpression qualifier,
                                                                     PsiClass aClass) {
    PsiElement placeToSearchEnclosingFrom;
    if (qualifier != null) {
      PsiType qType = qualifier.getType();
      placeToSearchEnclosingFrom = PsiUtil.resolveClassInType(qType);
    }
    else {
      placeToSearchEnclosingFrom = element;
    }
    return checkCreateInnerClassFromStaticContext(element, placeToSearchEnclosingFrom, aClass);
  }

  @Nullable
  static HighlightInfo checkCreateInnerClassFromStaticContext(PsiElement element,
                                                              PsiElement placeToSearchEnclosingFrom,
                                                              PsiClass aClass) {
    if (aClass == null || !PsiUtil.isInnerClass(aClass)) return null;
    PsiClass outerClass = aClass.getContainingClass();
    if (outerClass == null) return null;

    if (outerClass instanceof PsiSyntheticClass || InheritanceUtil.hasEnclosingInstanceInScope(outerClass, placeToSearchEnclosingFrom, true,
                                                                                               false)) return null;
    return reportIllegalEnclosingUsage(placeToSearchEnclosingFrom, aClass, outerClass, element);
  }

  @Nullable
  static HighlightInfo checkSuperQualifierType(@NotNull Project project, @NotNull PsiMethodCallExpression superCall) {
    if (!RefactoringChangeUtil.isSuperMethodCall(superCall)) return null;
    PsiMethod ctr = PsiTreeUtil.getParentOfType(superCall, PsiMethod.class, true, PsiMember.class);
    if (ctr == null) return null;
    final PsiClass aClass = ctr.getContainingClass();
    if (aClass == null) return null;
    PsiClass targetClass = aClass.getSuperClass();
    if (targetClass == null) return null;
    PsiExpression qualifier = superCall.getMethodExpression().getQualifierExpression();
    if (qualifier != null) {
      if (PsiUtil.isInnerClass(targetClass)) {
        PsiClass outerClass = targetClass.getContainingClass();
        if (outerClass != null) {
          PsiClassType outerType = JavaPsiFacade.getInstance(project).getElementFactory().createType(outerClass);
          return HighlightUtil.checkAssignability(outerType, null, qualifier, qualifier);
        }
      } else {
        String description = "'" + HighlightUtil.formatClass(targetClass) + "' is not an inner class";
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description).create();
      }
    }
    return null;
  }

  @Nullable
  static HighlightInfo reportIllegalEnclosingUsage(PsiElement place,
                                                   @Nullable PsiClass aClass,
                                                   PsiClass outerClass,
                                                   PsiElement elementToHighlight) {
    if (outerClass != null && !PsiTreeUtil.isContextAncestor(outerClass, place, false)) {
      String description = JavaErrorMessages.message("is.not.an.enclosing.class", HighlightUtil.formatClass(outerClass));
      HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description).create();
      registerMakeInnerClassStatic(highlightInfo, aClass);
      return highlightInfo;
    }
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, outerClass);
    if (staticParent != null) {
      String element = outerClass == null ? "" : HighlightUtil.formatClass(outerClass) + "." +
                                                 (place instanceof PsiSuperExpression ? PsiKeyword.SUPER : PsiKeyword.THIS);
      String description = JavaErrorMessages.message("cannot.be.referenced.from.static.context", element);
      HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description).create();
      // make context not static or referenced class static
      QuickFixAction.registerQuickFixAction(highlightInfo,
                                            QUICK_FIX_FACTORY.createModifierListFix(staticParent, PsiModifier.STATIC, false, false));
      if (aClass != null && HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, aClass.getModifierList()) == null) {
        QuickFixAction.registerQuickFixAction(highlightInfo,
                                              QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, true, false));
      }
      return highlightInfo;
    }
    return null;
  }
}
