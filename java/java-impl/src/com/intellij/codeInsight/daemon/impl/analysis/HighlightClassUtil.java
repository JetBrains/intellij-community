/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.impl.RefCountHolder;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class HighlightClassUtil {
  public static final String INTERFACE_EXPECTED = JavaErrorMessages.message("interface.expected");
  public static final String NO_INTERFACE_EXPECTED = JavaErrorMessages.message("no.interface.expected");
  private static final String STATIC_DECLARATION_IN_INNER_CLASS = JavaErrorMessages.message("static.declaration.in.inner.class");
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  /**
   * new ref(...) or new ref(..) { ... } where ref is abstract class
   */
  static HighlightInfo checkAbstractInstantiation(PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    HighlightInfo highlightInfo = null;
    if (parent instanceof PsiNewExpression && !PsiUtilBase.hasErrorElementChild(parent)) {
      if (((PsiNewExpression)parent).getType() instanceof PsiArrayType) return null;
      PsiElement refElement = ref.resolve();
      if (refElement instanceof PsiClass) {
        highlightInfo = checkInstantiationOfAbstractClass((PsiClass)refElement, ref);
      }
    }
    else if (parent instanceof PsiAnonymousClass
             && parent.getParent() instanceof PsiNewExpression
             && !PsiUtilBase.hasErrorElementChild(parent.getParent())) {
      PsiAnonymousClass aClass = (PsiAnonymousClass)parent;
      highlightInfo = checkClassWithAbstractMethods(aClass, ref.getTextRange());
    }
    return highlightInfo;
  }

  static HighlightInfo checkClassWithAbstractMethods(PsiClass aClass, TextRange textRange) {
    PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(aClass);

    if (abstractMethod == null || abstractMethod.getContainingClass() == null) {
      return null;
    }
    String baseClassName = HighlightUtil.formatClass(aClass, false);
    String methodName = HighlightUtil.formatMethod(abstractMethod);
    String message = JavaErrorMessages.message("class.must.be.abstract",
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

  static HighlightInfo checkClassMustBeAbstract(final PsiClass aClass, final TextRange textRange) {
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) || aClass.getRBrace() == null
        || aClass.isEnum() && hasEnumConstants(aClass)
      ) {
      return null;
    }
    return checkClassWithAbstractMethods(aClass, textRange);
  }


  public static HighlightInfo checkInstantiationOfAbstractClass(PsiClass aClass, PsiElement highlighElement) {
    HighlightInfo errorResult = null;
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      String baseClassName = aClass.getName();
      String message = JavaErrorMessages.message("abstract.cannot.be.instantiated", baseClassName);
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, highlighElement, message);
      if (!aClass.isInterface() && ClassUtil.getAnyAbstractMethod(aClass) == null) {
        // suggest to make not abstract only if possible
        IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.ABSTRACT, false, false);
        QuickFixAction.registerQuickFixAction(errorResult, fix);
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
    Module module = ModuleUtil.findModuleForPsiElement(aClass);
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
        if (element instanceof PsiMethod || element instanceof PsiClass) {
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



  static HighlightInfo checkPublicClassInRightFile(PsiKeyword keyword, PsiModifierList psiModifierList) {
    // todo most testcase classes located in wrong files
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
    if (virtualFile != null && !aClass.getName().equals(virtualFile.getNameWithoutExtension()) && aClass.getNameIdentifier() != null) {
      String message = JavaErrorMessages.message("public.class.should.be.named.after.file", aClass.getName());

      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, aClass.getNameIdentifier(), message);
      IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(psiModifierList, PsiModifier.PUBLIC, false, false);
      QuickFixAction.registerQuickFixAction(errorResult, fix);
      PsiClass[] classes = file.getClasses();
      if (classes.length > 1) {
        QuickFixAction.registerQuickFixAction(errorResult, new MoveClassToSeparateFileFix(aClass));
      }
      for (PsiClass otherClass : classes) {
        if (!otherClass.getManager().areElementsEquivalent(otherClass, aClass) && otherClass.hasModifierProperty(PsiModifier.PUBLIC)
            && otherClass.getName().equals(virtualFile.getNameWithoutExtension())) {
          return errorResult;
        }
      }
      QuickFixAction.registerQuickFixAction(errorResult, new RenameFileFix(aClass.getName() + "." + StdFileTypes.JAVA.getDefaultExtension()));
      QuickFixAction.registerQuickFixAction(errorResult, new RenameElementFix(aClass));
    }
    return errorResult;
  }


  private static HighlightInfo checkStaticFieldDeclarationInInnerClass(PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiField.class) == null) {
      return null;
    }
    PsiField field = (PsiField)keyword.getParent().getParent();
    if (PsiUtilBase.hasErrorElementChild(field)) return null;
    // except compile time constants
    if (PsiUtil.isCompileTimeConstant(field)) {
      return null;
    }
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                  keyword,
                                                                  STATIC_DECLARATION_IN_INNER_CLASS);
    IntentionAction fix1 = QUICK_FIX_FACTORY.createModifierListFix(field, PsiModifier.STATIC, false, false);
    QuickFixAction.registerQuickFixAction(errorResult, fix1);
    IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(field.getContainingClass(), PsiModifier.STATIC, true, false);
    QuickFixAction.registerQuickFixAction(errorResult, fix);
    return errorResult;
  }


  private static HighlightInfo checkStaticMethodDeclarationInInnerClass(PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiMethod.class) == null) {
      return null;
    }
    PsiMethod method = (PsiMethod)keyword.getParent().getParent();
    if (PsiUtilBase.hasErrorElementChild(method)) return null;
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                  keyword,
                                                                  STATIC_DECLARATION_IN_INNER_CLASS);
    IntentionAction fix1 = QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.STATIC, false, false);
    QuickFixAction.registerQuickFixAction(errorResult, fix1);
    IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix((PsiClass)keyword.getParent().getParent().getParent(), PsiModifier.STATIC, true, false);
    QuickFixAction.registerQuickFixAction(errorResult, fix);
    return errorResult;
  }


  private static HighlightInfo checkStaticInitializerDeclarationInInnerClass(PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiClassInitializer.class) == null) {
      return null;
    }
    PsiClassInitializer initializer = (PsiClassInitializer)keyword.getParent().getParent();
    if (PsiUtilBase.hasErrorElementChild(initializer)) return null;
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                  keyword,
                                                                  STATIC_DECLARATION_IN_INNER_CLASS);
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
    if (PsiUtilBase.hasErrorElementChild(aClass)) return null;
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
    HighlightInfo errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, STATIC_DECLARATION_IN_INNER_CLASS);
    if (context != keyword) {
      IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, false, false);
      QuickFixAction.registerQuickFixAction(errorResult, fix);
    }
    IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(aClass.getContainingClass(), PsiModifier.STATIC, true, false);
    QuickFixAction.registerQuickFixAction(errorResult, fix);
    return errorResult;
  }


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

  static HighlightInfo checkImplementsAllowed(PsiReferenceList list) {
    if (list.getParent() instanceof PsiClass) {
      PsiClass aClass = (PsiClass)list.getParent();
      if (aClass.isInterface()) {
        boolean isImplements = list.equals(aClass.getImplementsList());
        if (isImplements) {
          return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, JavaErrorMessages.message("implements.after.interface"));
        }
      }
    }
    return null;
  }


  static HighlightInfo checkExtendsClassAndImplementsInterface(PsiReferenceList referenceList,
                                                               JavaResolveResult resolveResult,
                                                               PsiJavaCodeReferenceElement context) {
    PsiClass aClass = (PsiClass)referenceList.getParent();
    boolean isImplements = referenceList.equals(aClass.getImplementsList());
    boolean isInterface = aClass.isInterface();
    if (isInterface && isImplements) return null;
    boolean mustBeInterface = isImplements || isInterface;
    HighlightInfo errorResult = null;
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (extendFrom.isInterface() != mustBeInterface) {
      errorResult = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                      context,
                                                      mustBeInterface ? INTERFACE_EXPECTED : NO_INTERFACE_EXPECTED);
      PsiClassType type =
        JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(extendFrom, resolveResult.getSubstitutor());
      QuickFixAction.registerQuickFixAction(errorResult, new ChangeExtendsToImplementsFix(aClass, type));
    }
    return errorResult;
  }


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


  static HighlightInfo checkAnonymousInheritFinal(PsiNewExpression expression) {
    PsiAnonymousClass aClass = PsiTreeUtil.getChildOfType(expression, PsiAnonymousClass.class);
    if (aClass == null) return null;
    PsiClassType baseClassReference = aClass.getBaseClassType();
    PsiClass baseClass = baseClassReference.resolve();
    if (baseClass == null) return null;
    return checkCannotInheritFromFinal(baseClass, aClass.getBaseClassReference());
  }




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
            return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
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


  static HighlightInfo checkInterfaceCannotBeLocal(PsiClass aClass) {
    if (PsiUtil.isLocalClass(aClass)) {
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               textRange,
                                               JavaErrorMessages.message("interface.cannot.be.local"));
    }
    return null;
  }


  public static HighlightInfo checkCyclicInheritance(PsiClass aClass) {
    PsiClass circularClass = getCircularClass(aClass, new HashSet<PsiClass>());
    if (circularClass != null) {
      String description = JavaErrorMessages.message("cyclic.inheritance", HighlightUtil.formatClass(circularClass));
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, description);
    }
    return null;
  }

  private static PsiClass getCircularClass(PsiClass aClass, Collection<PsiClass> usedClasses) {
    if (usedClasses.contains(aClass)) {
      return aClass;
    }
    try {
      usedClasses.add(aClass);
      PsiClass[] superTypes = aClass.getSupers();
      for (PsiElement superType : superTypes) {
        while (superType instanceof PsiClass) {
          if (!"java.lang.Object".equals(((PsiClass)superType).getQualifiedName())) {
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


  public static HighlightInfo checkThingNotAllowedInInterface(PsiElement element, PsiClass aClass) {
    if (aClass == null || !aClass.isInterface()) return null;
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                             element,
                                             JavaErrorMessages.message("not.allowed.in.interface"));
  }


  public static HighlightInfo checkQualifiedNewOfStaticClass(PsiNewExpression expression) {
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
    if (aClass != null && aClass.hasModifierProperty(PsiModifier.STATIC)) {
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                                                      expression,
                                                                      JavaErrorMessages.message("qualified.new.of.static.class"));
      if (!aClass.isEnum()) {
        IntentionAction fix = QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, false, false);
        QuickFixAction.registerQuickFixAction(info, fix);
      }
      QuickFixAction.registerQuickFixAction(info, new RemoveNewQualifierFix(expression, aClass));
      return info;
    }
    return null;
  }


  /**
   * class c extends foreign.inner {}
   *
   * @param extendRef points to the class in the extends list
   * @param resolved  extendRef resolved
   */
  public static HighlightInfo checkClassExtendsForeignInnerClass(PsiJavaCodeReferenceElement extendRef, PsiElement resolved) {
    PsiElement parent = extendRef.getParent();
    if (!(parent instanceof PsiReferenceList)) {
      return null;
    }
    PsiElement grand = parent.getParent();
    if (!(grand instanceof PsiClass)) {
      return null;
    }
    PsiClass aClass = (PsiClass)grand;
    if (aClass.getExtendsList() != parent || aClass instanceof PsiTypeParameter) {
      return null;
    }
    if (!(resolved instanceof PsiClass)) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, extendRef, JavaErrorMessages.message("class.name.expected"));
    }
    PsiClass base = (PsiClass)resolved;
    // must be inner class
    if (!PsiUtil.isInnerClass(base)) return null;
    PsiClass baseClass = base.getContainingClass();

    if (!hasEnclosingInstanceInScope(baseClass, extendRef, true) && !qualifiedNewCalledInConstructors(aClass, baseClass)) {
      String description = JavaErrorMessages.message("no.enclosing.instance.in.scope", HighlightUtil.formatClass(baseClass));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, extendRef, description);
    }

    return null;
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
      PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiReferenceExpression)) return false;
      PsiType type = qualifierExpression.getType();
      if (!(type instanceof PsiClassType)) return false;
      PsiClass resolved = ((PsiClassType)type).resolve();
      if (resolved != baseClass) return false;
    }
    return true;
  }


  static HighlightInfo checkExternalizableHasPublicNoArgsConstructor(PsiClass aClass, PsiElement context) {
    if (!isExternalizable(aClass) || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return null;
    }
    PsiMethod[] constructors = aClass.getConstructors();
    boolean hasPublicNoArgsConstructor = constructors.length == 0;
    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().getParametersCount() == 0 && constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
        hasPublicNoArgsConstructor = true;
        break;
      }
    }
    if (!hasPublicNoArgsConstructor) {
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.WARNING,
                                                                      context,
                                                                      JavaErrorMessages.message("externalizable.class.should.have.public.constructor"));
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createAddDefaultConstructorFix(aClass));
      return highlightInfo;
    }
    return null;
  }

  private static boolean isExternalizable(PsiClass aClass) {
    PsiManager manager = aClass.getManager();
    PsiClass externalizableClass =
      JavaPsiFacade.getInstance(manager.getProject()).findClass("java.io.Externalizable", aClass.getResolveScope());
    return externalizableClass != null && aClass.isInheritor(externalizableClass, true);
  }

  public static boolean hasEnclosingInstanceInScope(PsiClass aClass, PsiElement scope, final boolean isSuperClassAccepted) {
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
      }
      if (place instanceof PsiModifierListOwner && ((PsiModifierListOwner)place).hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      place = place.getParent();
    }
    return place == aClass;
  }

  @Nullable
  public static HighlightInfo checkCreateInnerClassFromStaticContext(PsiNewExpression expression) {
    PsiType type = expression.getType();
    if (type == null || type instanceof PsiArrayType || type instanceof PsiPrimitiveType) return null;
    PsiClass aClass = PsiUtil.resolveClassInType(type);
    if (aClass == null) return null;
    if (aClass instanceof PsiAnonymousClass) {
      aClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
      if (aClass == null) return null;
    }

    if (!PsiUtil.isInnerClass(aClass)) return null;
    PsiClass outerClass = aClass.getContainingClass();
    if (outerClass == null) return null;

    PsiElement placeToSearchEnclosingFrom;
    PsiExpression qualifier = expression.getQualifier();
    if (qualifier != null) {
      PsiType qtype = qualifier.getType();
      placeToSearchEnclosingFrom = PsiUtil.resolveClassInType(qtype);
    }
    else {
      placeToSearchEnclosingFrom = expression;
    }

    if (outerClass instanceof JspClass
        || hasEnclosingInstanceInScope(outerClass, placeToSearchEnclosingFrom, true)) return null;
    return reportIllegalEnclosingUsage(placeToSearchEnclosingFrom, aClass, outerClass, expression);
  }

  public static HighlightInfo checkSuperQualifierType(PsiMethodCallExpression superCall) {
    if (!HighlightUtil.isSuperMethodCall(superCall)) return null;
    PsiMethod ctr = PsiTreeUtil.getParentOfType(superCall, PsiMethod.class, true, PsiMember.class);
    if (ctr == null) return null;
    PsiClass targetClass = ctr.getContainingClass().getSuperClass();
    if (targetClass == null) return null;
    PsiExpression qualifier = superCall.getMethodExpression().getQualifierExpression();
    if (qualifier != null && PsiUtil.isInnerClass(targetClass)) {
      PsiClass outerClass = targetClass.getContainingClass();
      PsiClassType outerType = JavaPsiFacade.getInstance(superCall.getProject()).getElementFactory().createType(outerClass);
      return HighlightUtil.checkAssignability(outerType, null, qualifier, qualifier);
    }
    return null;
  }

  @Nullable
  public static HighlightInfo reportIllegalEnclosingUsage(PsiElement place,
                                                          PsiClass aClass, PsiClass outerClass,
                                                          PsiElement elementToHighlight) {
    if (outerClass != null && !PsiTreeUtil.isContextAncestor(outerClass, place, false)) {
      String description = JavaErrorMessages.message("is.not.an.enclosing.class", HighlightUtil.formatClass(outerClass));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, elementToHighlight, description);
    }
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, outerClass);
    if (staticParent != null) {
      String description = JavaErrorMessages.message("cannot.be.referenced.from.static.context",
                                                     outerClass == null ? "" : HighlightUtil.formatClass(outerClass) + "." + PsiKeyword.THIS);
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

}
