/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.RefCountHolder;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class HighlightClassUtil {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  /**
   * new ref(...) or new ref(..) { ... } where ref is abstract class
   */
  @Nullable
  static HighlightInfo checkAbstractInstantiation(PsiJavaCodeReferenceElement ref, PsiElement resolved) {
    PsiElement parent = ref.getParent();
    HighlightInfo highlightInfo = null;
    if (parent instanceof PsiNewExpression && !PsiUtilCore.hasErrorElementChild(parent)) {
      if (((PsiNewExpression)parent).getType() instanceof PsiArrayType) return null;
      if (resolved instanceof PsiClass) {
        highlightInfo = checkInstantiationOfAbstractClass((PsiClass)resolved, ref);
      }
    }
    else if (parent instanceof PsiAnonymousClass
             && parent.getParent() instanceof PsiNewExpression
             && !PsiUtilCore.hasErrorElementChild(parent.getParent())) {
      PsiAnonymousClass aClass = (PsiAnonymousClass)parent;
      highlightInfo = checkClassWithAbstractMethods(aClass, ref.getTextRange());
    }
    return highlightInfo;
  }

  @Nullable
  static HighlightInfo checkClassWithAbstractMethods(PsiClass aClass, TextRange textRange) {
    PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(aClass);

    if (abstractMethod == null || abstractMethod.getContainingClass() == null) {
      return null;
    }
    String baseClassName = HighlightUtil.formatClass(aClass, false);
    String methodName = HighlightUtil.formatMethod(abstractMethod);
    String message = JavaErrorMessages.message(aClass instanceof PsiEnumConstantInitializer ? "enum.constant.should.implement.method" : "class.must.be.abstract",
                                               baseClassName,
                                               methodName,
                                               HighlightUtil.formatClass(abstractMethod.getContainingClass(), false));

    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
    if (ClassUtil.getAnyMethodToImplement(aClass) != null) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createImplementMethodsFix(aClass));
    }
    if (!(aClass instanceof PsiAnonymousClass)
        && HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, aClass.getModifierList()) == null) {
      IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.ABSTRACT, true, false);
      QuickFixAction.registerQuickFixAction(errorResult, fix);
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
  public static HighlightInfo checkInstantiationOfAbstractClass(PsiClass aClass, PsiElement highlightElement) {
    HighlightInfo errorResult = null;
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      String baseClassName = aClass.getName();
      String message = JavaErrorMessages.message("abstract.cannot.be.instantiated", baseClassName);
      PsiElement parent = highlightElement.getParent();
      if (parent instanceof PsiNewExpression) {
        errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, parent, message);
      } else {
        errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, highlightElement, message);
      }
      final PsiMethod anyAbstractMethod = ClassUtil.getAnyAbstractMethod(aClass);
      if (!aClass.isInterface() && anyAbstractMethod == null) {
        // suggest to make not abstract only if possible
        IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.ABSTRACT, false, false);
        QuickFixAction.registerQuickFixAction(errorResult, fix);
      }
      if (anyAbstractMethod != null && parent instanceof PsiNewExpression && ((PsiNewExpression)parent).getClassReference() != null) {
        QuickFixAction.registerQuickFixAction(errorResult, new ImplementAbstractClassMethodsFix(parent));
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

    PsiClass[] classes = JavaPsiFacade.getInstance(aClass.getProject()).findClasses(qualifiedName, GlobalSearchScope.moduleScope(module));
    if (classes.length < numOfClassesToFind) return null;
    String dupFileName = null;
    for (PsiClass dupClass : classes) {
      // do not use equals
      if (dupClass != aClass) {
        VirtualFile file = dupClass.getContainingFile().getVirtualFile();
        if (file != null && manager.isInProject(dupClass)) {
          dupFileName = FileUtil.toSystemDependentName(file.getPath());
          break;
        }
      }
    }
    if (dupFileName == null) return null;
    String message = JavaErrorMessages.message("duplicate.class.in.other.file", dupFileName);
    TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);

    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
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
        if (element instanceof PsiMethod || element instanceof PsiClass || (element instanceof PsiCodeBlock && element.getParent() instanceof PsiClassInitializer)) {
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
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkPublicClassInRightFile(PsiKeyword keyword, PsiModifierList psiModifierList) {
    // most test case classes are located in wrong files
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;

    if (new PsiMatcherImpl(keyword)
      .dot(PsiMatchers.hasText(PsiModifier.PUBLIC))
      .parent(PsiMatchers.hasClass(PsiModifierList.class))
      .parent(PsiMatchers.hasClass(PsiClass.class))
      .parent(PsiMatchers.hasClass(PsiJavaFile.class))
      .getElement() == null) {
      return null;
    }

    PsiClass aClass = (PsiClass)keyword.getParent().getParent();
    PsiJavaFile file = (PsiJavaFile)aClass.getContainingFile();
    VirtualFile virtualFile = file.getVirtualFile();
    HighlightInfo errorResult = null;
    if (virtualFile != null && !aClass.getName().equals(virtualFile.getNameWithoutExtension())) {
      String message = JavaErrorMessages.message("public.class.should.be.named.after.file", aClass.getName());
      TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, range, message);
      IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(psiModifierList, PsiModifier.PUBLIC, false, false);
      QuickFixAction.registerQuickFixAction(errorResult, fix);
      PsiClass[] classes = file.getClasses();
      if (classes.length > 1) {
        QuickFixAction.registerQuickFixAction(errorResult, new MoveClassToSeparateFileFix(aClass));
      }
      for (PsiClass otherClass : classes) {
        if (!otherClass.getManager().areElementsEquivalent(otherClass, aClass) &&
            otherClass.hasModifierProperty(PsiModifier.PUBLIC) &&
            otherClass.getName().equals(virtualFile.getNameWithoutExtension())) {
          return errorResult;
        }
      }
      QuickFixAction.registerQuickFixAction(errorResult, new RenameFileFix(aClass.getName() + "." + StdFileTypes.JAVA.getDefaultExtension()));
      QuickFixAction.registerQuickFixAction(errorResult, new RenameElementFix(aClass));
    }
    return errorResult;
  }

  @Nullable
  static HighlightInfo checkClassAndPackageConflict(@NotNull PsiClass aClass) {
    String name = aClass.getQualifiedName();

    if (CommonClassNames.DEFAULT_PACKAGE.equals(name)) {
      String message = JavaErrorMessages.message("class.clashes.with.package", name);
      TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, range, message);
    }

    PsiElement file = aClass.getParent();
    if (file instanceof PsiJavaFile && !((PsiJavaFile)file).getPackageName().isEmpty()) {
      PsiElement directory = file.getParent();
      if (directory instanceof PsiDirectory && ((PsiDirectory)directory).findSubdirectory(aClass.getName()) != null) {
        String message = JavaErrorMessages.message("class.clashes.with.package", name);
        TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, range, message);
      }
    }

    return null;
  }

  @Nullable
  private static HighlightInfo checkStaticFieldDeclarationInInnerClass(PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiField.class) == null) {
      return null;
    }

    PsiField field = (PsiField)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(field) || PsiUtil.isCompileTimeConstant(field)) {
      return null;
    }

    String message = JavaErrorMessages.message("static.declaration.in.inner.class");
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, keyword, message);

    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(field, PsiModifier.STATIC, false, false));

    PsiClass aClass = field.getContainingClass();
    if (aClass != null) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, true, false));
    }

    return errorResult;
  }

  @Nullable
  private static HighlightInfo checkStaticMethodDeclarationInInnerClass(PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiMethod.class) == null) {
      return null;
    }
    PsiMethod method = (PsiMethod)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(method)) return null;
    String message = JavaErrorMessages.message("static.declaration.in.inner.class");
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, keyword, message);
    IntentionAction fix1 = QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.STATIC, false, false);
    QuickFixAction.registerQuickFixAction(errorResult, fix1);
    IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix((PsiClass)keyword.getParent().getParent().getParent(), PsiModifier.STATIC, true, false);
    QuickFixAction.registerQuickFixAction(errorResult, fix);
    return errorResult;
  }

  @Nullable
  private static HighlightInfo checkStaticInitializerDeclarationInInnerClass(PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiClassInitializer.class) == null) {
      return null;
    }
    PsiClassInitializer initializer = (PsiClassInitializer)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(initializer)) return null;
    String message = JavaErrorMessages.message("static.declaration.in.inner.class");
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, keyword, message);
    IntentionAction fix1 = QUICK_FIX_FACTORY.createModifierListFix(initializer, PsiModifier.STATIC, false, false);
    QuickFixAction.registerQuickFixAction(errorResult, fix1);
    PsiClass owner = (PsiClass)keyword.getParent().getParent().getParent();
    IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(owner, PsiModifier.STATIC, true, false);
    QuickFixAction.registerQuickFixAction(errorResult, fix);
    return errorResult;
  }

  private static PsiElement getEnclosingStaticClass(PsiKeyword keyword, Class<?> parentClass) {
    return new PsiMatcherImpl(keyword)
      .dot(PsiMatchers.hasText(PsiModifier.STATIC))
      .parent(PsiMatchers.hasClass(PsiModifierList.class))
      .parent(PsiMatchers.hasClass(parentClass))
      .parent(PsiMatchers.hasClass(PsiClass.class))
      .dot(PsiMatchers.hasModifier(PsiModifier.STATIC, false))
      .parent(PsiMatchers.hasClass(new Class[]{PsiClass.class, PsiDeclarationStatement.class, PsiNewExpression.class, PsiEnumConstant.class}))
      .getElement();
  }

  @Nullable
  private static HighlightInfo checkStaticClassDeclarationInInnerClass(PsiKeyword keyword) {
    // keyword points to 'class' or 'interface' or 'enum'
    if (new PsiMatcherImpl(keyword)
      .parent(PsiMatchers.hasClass(PsiClass.class))
      .dot(PsiMatchers.hasModifier(PsiModifier.STATIC, true))
      .parent(PsiMatchers.hasClass(PsiClass.class))
      .dot(PsiMatchers.hasModifier(PsiModifier.STATIC, false))
      .parent(PsiMatchers.hasClass(new Class[]{PsiClass.class, PsiDeclarationStatement.class, PsiNewExpression.class, PsiEnumConstant.class}))
      .getElement() == null) {
      return null;
    }
    PsiClass aClass = (PsiClass)keyword.getParent();
    if (PsiUtilCore.hasErrorElementChild(aClass) || (aClass.getQualifiedName() == null && !aClass.isInterface())) return null;
    // highlight 'static' keyword if any, or class or interface if not
    PsiElement context = null;
    PsiModifierList modifierList = aClass.getModifierList();
    PsiElement[] children = modifierList.getChildren();
    for (PsiElement element : children) {
      if (Comparing.equal(element.getText(), PsiModifier.STATIC)) {
        context = element;
        break;
      }
    }
    TextRange textRange = context == null ? null : context.getTextRange();
    if (textRange == null) {
      textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    }
    String message = JavaErrorMessages.message("static.declaration.in.inner.class");
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
    if (context != keyword) {
      IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, false, false);
      QuickFixAction.registerQuickFixAction(errorResult, fix);
    }
    IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(aClass.getContainingClass(), PsiModifier.STATIC, true, false);
    QuickFixAction.registerQuickFixAction(errorResult, fix);
    return errorResult;
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
          return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, JavaErrorMessages.message("extends.after.enum"));
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
          final HighlightInfo highlightInfo =
            HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, JavaErrorMessages.message("implements.after.interface"));
          final PsiClassType[] referencedTypes = list.getReferencedTypes();
          if (referencedTypes.length > 0) {
            QuickFixAction.registerQuickFixAction(highlightInfo, new ChangeExtendsToImplementsFix(aClass, referencedTypes[0]));
          }
          return highlightInfo;
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
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, ref, message);
      PsiClassType type =
        JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(ref);
      QuickFixAction.registerQuickFixAction(errorResult, new ChangeExtendsToImplementsFix(aClass, type));
    }
    return errorResult;
  }

  @Nullable
  static HighlightInfo checkCannotInheritFromFinal(PsiClass superClass, PsiElement elementToHighlight) {
    HighlightInfo errorResult = null;
    if (superClass.hasModifierProperty(PsiModifier.FINAL) || superClass.isEnum()) {
      String message = JavaErrorMessages.message("inheritance.from.final.class", superClass.getQualifiedName());
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, elementToHighlight, message);
      IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(superClass, PsiModifier.FINAL, false, false);
      QuickFixAction.registerQuickFixAction(errorResult, fix);
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
    List<PsiClassType> exceptions = new ArrayList<PsiClassType>();
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
  public static HighlightInfo checkClassDoesNotCallSuperConstructorOrHandleExceptions(PsiClass aClass,
                                                                                      RefCountHolder refCountHolder,
                                                                                      final PsiResolveHelper resolveHelper) {
    if (aClass.isEnum()) return null;
    // check only no-ctr classes. Problem with specific constructor will be highlighted inside it
    if (aClass.getConstructors().length != 0) return null;
    // find no-args base class ctr
    TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    return checkBaseClassDefaultConstructorProblem(aClass, refCountHolder, resolveHelper, textRange, PsiClassType.EMPTY_ARRAY);
  }

  public static HighlightInfo checkBaseClassDefaultConstructorProblem(PsiClass aClass,
                                                                      RefCountHolder refCountHolder,
                                                                      PsiResolveHelper resolveHelper,
                                                                      TextRange textRange,
                                                                      @NotNull PsiClassType[] handledExceptions) {
    PsiClass baseClass = aClass.getSuperClass();
    if (baseClass == null) return null;
    PsiMethod[] constructors = baseClass.getConstructors();
    if (constructors.length == 0) return null;

    for (PsiMethod constructor : constructors) {
      if (resolveHelper.isAccessible(constructor, aClass, null)) {
        if (constructor.getParameterList().getParametersCount() == 0 ||
            constructor.getParameterList().getParametersCount() == 1 && constructor.isVarArgs()
          ) {
          // it is an error if base ctr throws exceptions
          String description = checkDefaultConstructorThrowsException(constructor, handledExceptions);
          if (description != null) {
            final HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
            QuickFixAction.registerQuickFixAction(info, new CreateConstructorMatchingSuperFix(aClass));
            return info;
          }
          if (refCountHolder != null) {
            refCountHolder.registerLocallyReferenced(constructor);
          }
          return null;
        }
      }
    }

    String description = JavaErrorMessages.message("no.default.constructor.available", HighlightUtil.formatClass(baseClass));

    HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
    QuickFixAction.registerQuickFixAction(info, new CreateConstructorMatchingSuperFix(aClass));

    return info;
  }

  @Nullable
  static HighlightInfo checkInterfaceCannotBeLocal(PsiClass aClass) {
    if (PsiUtil.isLocalClass(aClass)) {
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, JavaErrorMessages.message("interface.cannot.be.local"));
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkCyclicInheritance(PsiClass aClass) {
    PsiClass circularClass = getCircularClass(aClass, new HashSet<PsiClass>());
    if (circularClass != null) {
      String description = JavaErrorMessages.message("cyclic.inheritance", HighlightUtil.formatClass(circularClass));
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
    }
    return null;
  }

  @Nullable
  private static PsiClass getCircularClass(PsiClass aClass, Collection<PsiClass> usedClasses) {
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
  public static HighlightInfo checkExtendsDuplicate(PsiJavaCodeReferenceElement element, PsiElement resolved) {
    if (!(element.getParent() instanceof PsiReferenceList)) return null;
    PsiReferenceList list = (PsiReferenceList)element.getParent();
    if (!(list.getParent() instanceof PsiClass)) return null;
    if (!(resolved instanceof PsiClass)) return null;
    PsiClass aClass = (PsiClass)resolved;
    PsiClassType[] referencedTypes = list.getReferencedTypes();
    int dupCount = 0;
    for (PsiClassType referencedType : referencedTypes) {
      PsiClass resolvedElement = referencedType.resolve();
      if (resolvedElement != null && list.getManager().areElementsEquivalent(resolvedElement, aClass)) {
        dupCount++;
      }
    }
    if (dupCount > 1) {
      String description = JavaErrorMessages.message("duplicate.class", HighlightUtil.formatClass(aClass));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, element, description);
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkClassAlreadyImported(PsiClass aClass, PsiElement elementToHighlight) {
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
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 elementToHighlight,
                                                 description);
      }
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkClassExtendsOnlyOneClass(PsiReferenceList list) {
    PsiClassType[] referencedTypes = list.getReferencedTypes();
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiClass)) return null;

    PsiClass aClass = (PsiClass)parent;
    if (!aClass.isInterface()
        && referencedTypes.length > 1
        && aClass.getExtendsList() == list) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               list,
                                               JavaErrorMessages.message("class.cannot.extend.multiple.classes"));
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkThingNotAllowedInInterface(PsiElement element, PsiClass aClass) {
    if (aClass == null || !aClass.isInterface()) return null;
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, element, JavaErrorMessages.message("not.allowed.in.interface"));
  }

  @Nullable
  public static HighlightInfo checkQualifiedNew(PsiNewExpression expression) {
    PsiExpression qualifier = expression.getQualifier();
    if (qualifier == null) return null;
    PsiType type = expression.getType();
    if (type instanceof PsiArrayType) {
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                      expression,
                                                                      JavaErrorMessages.message("invalid.qualified.new"));
      QuickFixAction.registerQuickFixAction(info, new RemoveNewQualifierFix(expression, null));
      return info;
    }
    PsiClass aClass = PsiUtil.resolveClassInType(type);
    HighlightInfo info = null;
    if (aClass != null) {
      if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
        info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                 expression,
                                                 JavaErrorMessages.message("qualified.new.of.static.class"));
        if (!aClass.isEnum()) {
          IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, false, false);
          QuickFixAction.registerQuickFixAction(info, fix);
        }
        
      } else if (aClass instanceof PsiAnonymousClass) {
        final PsiClass baseClass = PsiUtil.resolveClassInType(((PsiAnonymousClass)aClass).getBaseClassType());
        if (baseClass != null && baseClass.isInterface()) {
          info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                   expression,
                                                   "Anonymous class implements interface; cannot have qualifier for new");
        }
      }
      QuickFixAction.registerQuickFixAction(info, new RemoveNewQualifierFix(expression, aClass));
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
  public static HighlightInfo checkClassExtendsForeignInnerClass(final PsiJavaCodeReferenceElement extendRef, final PsiElement resolved) {
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
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, extendRef, JavaErrorMessages.message("class.name.expected"));
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
            infos[0] = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, extendRef, description);
            return;
          }

          // must be inner class
          if (!PsiUtil.isInnerClass(base)) return;

          if (resolve == resolved && baseClass != null && !PsiTreeUtil.isAncestor(baseClass, extendRef, true) &&
              !hasEnclosingInstanceInScope(baseClass, extendRef, true, true) && !qualifiedNewCalledInConstructors(aClass, baseClass)) {
            String description = JavaErrorMessages.message("no.enclosing.instance.in.scope", HighlightUtil.formatClass(baseClass));
            infos[0] = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, extendRef, description);
          }
        }
      }
    });

    return infos[0];
  }

  private static boolean qualifiedNewCalledInConstructors(final PsiClass aClass, final PsiClass baseClass) {
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
      if (!HighlightUtil.isSuperOrThisMethodCall(expression)) return false;
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      if (PsiKeyword.THIS.equals(methodCallExpression.getMethodExpression().getReferenceName())) continue;
      PsiReferenceExpression referenceExpression = methodCallExpression.getMethodExpression();
      PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(referenceExpression.getQualifierExpression());
      if (!(qualifierExpression instanceof PsiReferenceExpression) && !(qualifierExpression instanceof PsiNewExpression)) return false;
      PsiType type = qualifierExpression.getType();
      if (!(type instanceof PsiClassType)) return false;
      PsiClass resolved = ((PsiClassType)type).resolve();
      if (resolved != baseClass) return false;
    }
    return true;
  }

  public static boolean hasEnclosingInstanceInScope(PsiClass aClass,
                                                    PsiElement scope,
                                                    final boolean isSuperClassAccepted,
                                                    boolean isTypeParamsAccepted) {
    PsiManager manager = aClass.getManager();
    PsiElement place = scope;
    while (place != null && place != aClass && !(place instanceof PsiFile)) {
      if (place instanceof PsiClass) {
        if (isSuperClassAccepted) {
          if (InheritanceUtil.isInheritorOrSelf((PsiClass)place, aClass, true)) return true;
        }
        else {
          if (manager.areElementsEquivalent(place, aClass)) return true;
        }
        if (isTypeParamsAccepted && place instanceof PsiTypeParameter) {
          return true;
        }
      }
      if (place instanceof PsiModifierListOwner) {
        final PsiModifierList modifierList = ((PsiModifierListOwner)place).getModifierList();
        if (modifierList != null && modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
          return false;
        }
      }
      place = place.getParent();
    }
    return place == aClass;
  }

  @Nullable
  public static HighlightInfo checkCreateInnerClassFromStaticContext(PsiNewExpression expression) {
    PsiType type = expression.getType();
    PsiExpression qualifier = expression.getQualifier();
    if (type == null || type instanceof PsiArrayType || type instanceof PsiPrimitiveType) return null;
    PsiClass aClass = PsiUtil.resolveClassInType(type);
    if (aClass == null) return null;
    if (aClass instanceof PsiAnonymousClass) {
      aClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
      if (aClass == null) return null;
    }

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
  public static HighlightInfo checkCreateInnerClassFromStaticContext(PsiElement element,
                                                                     PsiElement placeToSearchEnclosingFrom,
                                                                     PsiClass aClass) {
    if (aClass == null || !PsiUtil.isInnerClass(aClass)) return null;
    PsiClass outerClass = aClass.getContainingClass();
    if (outerClass == null) return null;

    if (outerClass instanceof JspClass || hasEnclosingInstanceInScope(outerClass, placeToSearchEnclosingFrom, true, false)) return null;
    return reportIllegalEnclosingUsage(placeToSearchEnclosingFrom, aClass, outerClass, element);
  }

  @Nullable
  public static HighlightInfo checkSuperQualifierType(@NotNull Project project, @NotNull PsiMethodCallExpression superCall) {
    if (!HighlightUtil.isSuperMethodCall(superCall)) return null;
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
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, qualifier, "'" + HighlightUtil.formatClass(targetClass) + "' is not an inner class");
      }
    }
    return null;
  }

  @Nullable
  public static HighlightInfo reportIllegalEnclosingUsage(PsiElement place,
                                                          @Nullable PsiClass aClass,
                                                          PsiClass outerClass,
                                                          PsiElement elementToHighlight) {
    if (outerClass != null && !PsiTreeUtil.isContextAncestor(outerClass, place, false)) {
      String description = JavaErrorMessages.message("is.not.an.enclosing.class", HighlightUtil.formatClass(outerClass));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, elementToHighlight, description);
    }
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, outerClass);
    if (staticParent != null) {
      String element = outerClass == null ? "" : HighlightUtil.formatClass(outerClass) + "." +
                                                 (place instanceof PsiSuperExpression ? PsiKeyword.SUPER : PsiKeyword.THIS);
      String description = JavaErrorMessages.message("cannot.be.referenced.from.static.context", element);
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, elementToHighlight, description);
      // make context not static or referenced class static
      IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(staticParent, PsiModifier.STATIC, false, false);
      QuickFixAction.registerQuickFixAction(highlightInfo, fix);
      if (aClass != null && HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, aClass.getModifierList()) == null) {
        IntentionAction fix2 = QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, true, false);
        QuickFixAction.registerQuickFixAction(highlightInfo, fix2);
      }
      return highlightInfo;
    }
    return null;
  }

  private static class ImplementAbstractClassMethodsFix extends ImplementMethodsFix {
    public ImplementAbstractClassMethodsFix(PsiElement highlightElement) {
      super(highlightElement);
    }

    @Override
    public boolean isAvailable(@NotNull Project project,
                               @NotNull PsiFile file,
                               @NotNull PsiElement startElement,
                               @NotNull PsiElement endElement) {
      if (startElement instanceof PsiNewExpression) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        String startElementText = startElement.getText();
        try {
          PsiNewExpression newExpression =
            (PsiNewExpression)elementFactory.createExpressionFromText(startElementText + "{}", startElement);
          if (newExpression.getAnonymousClass() == null) {
            try {
              newExpression = (PsiNewExpression)elementFactory.createExpressionFromText(startElementText + "){}", startElement);
            }
            catch (IncorrectOperationException e) {
              return false;
            }
            if (newExpression.getAnonymousClass() == null) return false;
          }
        }
        catch (IncorrectOperationException e) {
          return false;
        }
        return true;
      }
      return false;
    }

    @Override
    public void invoke(@NotNull final Project project,
                       @NotNull PsiFile file,
                       @Nullable("is null when called from inspection") final Editor editor,
                       @NotNull final PsiElement startElement,
                       @NotNull PsiElement endElement) {
      final PsiFile containingFile = startElement.getContainingFile();
      if (editor == null || !CodeInsightUtilBase.prepareFileForWrite(containingFile)) return;
      PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)startElement).getClassReference();
      if (classReference == null) return;
      final PsiClass psiClass = (PsiClass)classReference.resolve();
      if (psiClass == null) return;
      final MemberChooser<PsiMethodMember> chooser = chooseMethodsToImplement(editor, startElement, psiClass);
      if (chooser == null) return;

      final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
      if (selectedElements == null || selectedElements.isEmpty()) return;

      new WriteCommandAction(project, file) {
        @Override
        protected void run(final Result result) throws Throwable {
          PsiNewExpression newExpression =
            (PsiNewExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText(startElement.getText() + "{}", startElement);
          newExpression = (PsiNewExpression)startElement.replace(newExpression);
          final PsiClass psiClass = newExpression.getAnonymousClass(); 
          if (psiClass == null) return;
          PsiClassType baseClassType = ((PsiAnonymousClass)psiClass).getBaseClassType();
          PsiClass resolve = baseClassType.resolve();
          if (resolve == null) return;
          PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(resolve, psiClass, PsiSubstitutor.EMPTY);
          for (PsiMethodMember selectedElement : selectedElements) {
            selectedElement.setSubstitutor(superClassSubstitutor);
          }
          OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor, psiClass, selectedElements, chooser.isCopyJavadoc(),
                                                                       chooser.isInsertOverrideAnnotation());
        }
      }.execute();
    }
  }
}
