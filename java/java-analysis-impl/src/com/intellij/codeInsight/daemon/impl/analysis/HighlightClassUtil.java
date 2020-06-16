// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Checks and Highlights problems with classes
 * User: cdr
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ClassUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.JavaPsiConstructorUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class HighlightClassUtil {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  /**
   * new ref(...) or new ref(..) { ... } where ref is abstract class
   */
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

  private static HighlightInfo checkClassWithAbstractMethods(@NotNull PsiClass aClass, @NotNull TextRange range) {
    return checkClassWithAbstractMethods(aClass, aClass, range);
  }

  static HighlightInfo checkClassWithAbstractMethods(@NotNull PsiClass aClass, @NotNull PsiElement implementsFixElement, @NotNull TextRange range) {
    PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(aClass);

    if (abstractMethod == null) {
      return null;
    }

    final PsiClass superClass = abstractMethod.getContainingClass();
    if (superClass == null) {
      return null;
    }

    final String messageKey;
    final String referenceName;
    if (aClass instanceof PsiEnumConstantInitializer) {
      messageKey = "enum.constant.must.implement.method";

      final PsiEnumConstantInitializer enumConstant = (PsiEnumConstantInitializer)aClass;
      referenceName = enumConstant.getEnumConstant().getName();
    }
    else if (aClass.isRecord() || implementsFixElement instanceof PsiEnumConstant) {
      messageKey = "class.must.implement.method";
      referenceName = HighlightUtil.formatClass(aClass, false);
    }
    else {
      messageKey = "class.must.be.abstract";
      referenceName = HighlightUtil.formatClass(aClass, false);
    }

    final String message = JavaErrorBundle.message(messageKey,
                                                   referenceName,
                                                   JavaHighlightUtil.formatMethod(abstractMethod),
                                                   HighlightUtil.formatClass(superClass, false));

    HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message).create();
    final PsiMethod anyMethodToImplement = ClassUtil.getAnyMethodToImplement(aClass);
    if (anyMethodToImplement != null) {
      if (!anyMethodToImplement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) ||
          JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, superClass)) {
        QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createImplementMethodsFix(implementsFixElement));
      } else {
        QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions(anyMethodToImplement, MemberRequestsKt.modifierRequest(JvmModifier.PROTECTED, true)));
        QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions(anyMethodToImplement, MemberRequestsKt.modifierRequest(JvmModifier.PUBLIC, true)));
      }
    }
    if (!(aClass instanceof PsiAnonymousClass) &&
        !aClass.isEnum()
        && aClass.getModifierList() != null
        && HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, aClass.getModifierList()) == null) {
      QuickFixAction.registerQuickFixAction(
        errorResult,
        QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.ABSTRACT, true, false)
      );
    }
    return errorResult;
  }

  static HighlightInfo checkClassMustBeAbstract(@NotNull PsiClass aClass, @NotNull TextRange textRange) {
    if (aClass.isEnum()) {
      if (hasEnumConstantsWithInitializer(aClass)) return null;
    }
    else if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) || aClass.getRBrace() == null) {
      return null;
    }
    return checkClassWithAbstractMethods(aClass, textRange);
  }

  static HighlightInfo checkInstantiationOfAbstractClass(@NotNull PsiClass aClass, @NotNull PsiElement highlightElement) {
    HighlightInfo errorResult = null;
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
        (!(highlightElement instanceof PsiNewExpression) || !(((PsiNewExpression)highlightElement).getType() instanceof PsiArrayType))) {
      String baseClassName = aClass.getName();
      String message = JavaErrorBundle.message("abstract.cannot.be.instantiated", baseClassName);
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(highlightElement).descriptionAndTooltip(message).create();
      final PsiMethod anyAbstractMethod = ClassUtil.getAnyAbstractMethod(aClass);
      if (!aClass.isInterface() && anyAbstractMethod == null) {
        // suggest to make not abstract only if possible
        QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions(aClass, MemberRequestsKt.modifierRequest(JvmModifier.ABSTRACT, false)));
      }
      if (anyAbstractMethod != null && highlightElement instanceof PsiNewExpression && ((PsiNewExpression)highlightElement).getClassReference() != null) {
        QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createImplementAbstractClassMethodsFix(highlightElement));
      }
    }
    return errorResult;
  }

  static boolean hasEnumConstantsWithInitializer(@NotNull PsiClass aClass) {
    return CachedValuesManager.getCachedValue(aClass, () -> {
      PsiField[] fields = aClass.getFields();
      for (PsiField field : fields) {
        if (field instanceof PsiEnumConstant && ((PsiEnumConstant)field).getInitializingClass() != null) {
          return new CachedValueProvider.Result<>(true, PsiModificationTracker.MODIFICATION_COUNT);
        }
      }
      return new CachedValueProvider.Result<>(false, PsiModificationTracker.MODIFICATION_COUNT);
    });

  }

  static HighlightInfo checkDuplicateTopLevelClass(@NotNull PsiClass aClass) {
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
    String message = JavaErrorBundle.message("duplicate.class.in.other.file", dupFileName);
    TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);

    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(message).create();
  }

  static HighlightInfo checkDuplicateNestedClass(@NotNull PsiClass aClass) {
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
      String message = JavaErrorBundle.message("duplicate.class", name);
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(message).create();
    }
    return null;
  }

  static HighlightInfo checkPublicClassInRightFile(@NotNull PsiClass aClass) {
    PsiFile containingFile = aClass.getContainingFile();
    if (aClass.getParent() != containingFile || !aClass.hasModifierProperty(PsiModifier.PUBLIC) || !(containingFile instanceof PsiJavaFile)) return null;
    PsiJavaFile file = (PsiJavaFile)containingFile;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null || virtualFile.getNameWithoutExtension().equals(aClass.getName())) {
      return null;
    }
    String message = JavaErrorBundle.message("public.class.should.be.named.after.file", aClass.getName());
    TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).
      range(aClass, range.getStartOffset(), range.getEndOffset()).
      descriptionAndTooltip(message).create();
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.PUBLIC, false, false));
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

  static HighlightInfo checkClassRestrictedKeyword(@NotNull LanguageLevel level, @NotNull PsiIdentifier identifier) {
    String className = identifier.getText();
    if (isRestrictedIdentifier(className, level)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .descriptionAndTooltip(JavaErrorBundle.message("restricted.identifier", className))
        .range(identifier)
        .create();
    }
    return null;
  }

  /**
   * @param typeName name of the type to test
   * @param level language level
   * @return true if given name cannot be used as a type name at given language level
   */
  public static boolean isRestrictedIdentifier(String typeName, @NotNull LanguageLevel level) {
    return PsiKeyword.VAR.equals(typeName) && HighlightingFeature.LVTI.isSufficient(level) ||
           PsiKeyword.YIELD.equals(typeName) && HighlightingFeature.SWITCH_EXPRESSION.isSufficient(level) ||
           PsiKeyword.RECORD.equals(typeName) && HighlightingFeature.RECORDS.isSufficient(level);
  }

  static HighlightInfo checkClassAndPackageConflict(@NotNull PsiClass aClass) {
    String name = aClass.getQualifiedName();

    if (CommonClassNames.DEFAULT_PACKAGE.equals(name)) {
      String message = JavaErrorBundle.message("class.clashes.with.package", name);
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
          String message = JavaErrorBundle.message("class.clashes.with.package", name);
          TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message).create();
        }
      }
    }

    return null;
  }

  private static HighlightInfo checkStaticFieldDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiField.class) == null) {
      return null;
    }

    PsiField field = (PsiField)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(field) || PsiUtil.isCompileTimeConstant(field)) {
      return null;
    }

    String message = JavaErrorBundle.message("static.declaration.in.inner.class");
    HighlightInfo result = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(message).create();

    QuickFixAction.registerQuickFixAction(result, QUICK_FIX_FACTORY.createModifierListFix(field, PsiModifier.STATIC, false, false));
    registerMakeInnerClassStatic(field.getContainingClass(), result);

    return result;
  }

  private static void registerMakeInnerClassStatic(@Nullable PsiClass aClass, @Nullable HighlightInfo result) {
    if (aClass != null && aClass.getContainingClass() != null) {
      QuickFixAction.registerQuickFixAction(result, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, true, false));
    }
  }

  private static HighlightInfo checkStaticMethodDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiMethod.class) == null) {
      return null;
    }
    PsiMethod method = (PsiMethod)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(method)) return null;
    String message = JavaErrorBundle.message("static.declaration.in.inner.class");
    HighlightInfo result = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(message).create();
    QuickFixAction.registerQuickFixAction(result, QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.STATIC, false, false));
    registerMakeInnerClassStatic((PsiClass)keyword.getParent().getParent().getParent(), result);
    return result;
  }

  private static HighlightInfo checkStaticInitializerDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiClassInitializer.class) == null) {
      return null;
    }
    PsiClassInitializer initializer = (PsiClassInitializer)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(initializer)) return null;
    String message = JavaErrorBundle.message("static.declaration.in.inner.class");
    HighlightInfo result = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(message).create();
    QuickFixAction.registerQuickFixAction(result, QUICK_FIX_FACTORY.createModifierListFix(initializer, PsiModifier.STATIC, false, false));
    registerMakeInnerClassStatic((PsiClass)keyword.getParent().getParent().getParent(), result);
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

  private static HighlightInfo checkStaticClassDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
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
      for (PsiElement element = modifierList.getFirstChild(); element != null; element = element.getNextSibling()) {
        if (Objects.equals(element.getText(), PsiModifier.STATIC)) {
          context = element;
          break;
        }
      }
    }
    TextRange range = context != null ? context.getTextRange() : HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    String message = JavaErrorBundle.message("static.declaration.in.inner.class");
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message).create();
    if (context != keyword) {
      QuickFixAction.registerQuickFixActions(info, null, JvmElementActionFactories.createModifierActions(aClass, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, false)));
    }
    PsiClass containingClass = aClass.getContainingClass();
    registerMakeInnerClassStatic(containingClass, info);
    return info;
  }

  static HighlightInfo checkStaticDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    HighlightInfo errorResult = checkStaticFieldDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    errorResult = checkStaticMethodDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    errorResult = checkStaticClassDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    errorResult = checkStaticInitializerDeclarationInInnerClass(keyword);
    return errorResult;
  }

  static HighlightInfo checkExtendsAllowed(@NotNull PsiReferenceList list) {
    if (list.getParent() instanceof PsiClass) {
      PsiClass aClass = (PsiClass)list.getParent();
      if (aClass.isEnum() || aClass.isRecord()) {
        boolean isExtends = list.equals(aClass.getExtendsList());
        if (isExtends) {
          String description = JavaErrorBundle.message(aClass.isRecord() ? "record.extends" : "extends.after.enum");
          HighlightInfo info =
            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).create();
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createDeleteFix(list));
          return info;
        }
      }
    }
    return null;
  }

  static HighlightInfo checkImplementsAllowed(@NotNull PsiReferenceList list) {
    if (list.getParent() instanceof PsiClass) {
      PsiClass aClass = (PsiClass)list.getParent();
      if (aClass.isInterface()) {
        boolean isImplements = list.equals(aClass.getImplementsList());
        if (isImplements) {
          String description = JavaErrorBundle.message("implements.after.interface");
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

  static HighlightInfo checkExtendsClassAndImplementsInterface(@NotNull PsiReferenceList referenceList,
                                                               @NotNull JavaResolveResult resolveResult,
                                                               @NotNull PsiJavaCodeReferenceElement ref) {
    PsiClass aClass = (PsiClass)referenceList.getParent();
    boolean isImplements = referenceList.equals(aClass.getImplementsList());
    boolean isInterface = aClass.isInterface();
    if (isInterface && isImplements) return null;
    boolean mustBeInterface = isImplements || isInterface;
    HighlightInfo errorResult = null;
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (extendFrom.isInterface() != mustBeInterface) {
      String message = JavaErrorBundle.message(mustBeInterface ? "interface.expected" : "no.interface.expected");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(message).create();
      PsiClassType type =
        JavaPsiFacade.getElementFactory(aClass.getProject()).createType(ref);
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createChangeExtendsToImplementsFix(aClass, type));
    }
    return errorResult;
  }

  static HighlightInfo checkCannotInheritFromFinal(@NotNull PsiClass superClass, @NotNull PsiElement elementToHighlight) {
    HighlightInfo errorResult = null;
    if (superClass.hasModifierProperty(PsiModifier.FINAL) || superClass.isEnum()) {
      String message = JavaErrorBundle
        .message("inheritance.from.final.class", superClass.getQualifiedName(), superClass.isEnum() ? PsiKeyword.ENUM : PsiKeyword.FINAL);
      errorResult =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions(superClass, MemberRequestsKt.modifierRequest(JvmModifier.FINAL, false)));
    }
    return errorResult;
  }

  static HighlightInfo checkAnonymousInheritFinal(@NotNull PsiNewExpression expression) {
    PsiAnonymousClass aClass = PsiTreeUtil.getChildOfType(expression, PsiAnonymousClass.class);
    if (aClass == null) return null;
    PsiClassType baseClassReference = aClass.getBaseClassType();
    PsiClass baseClass = baseClassReference.resolve();
    if (baseClass == null) return null;
    return checkCannotInheritFromFinal(baseClass, aClass.getBaseClassReference());
  }

  private static String checkDefaultConstructorThrowsException(@NotNull PsiMethod constructor, PsiClassType @NotNull [] handledExceptions) {
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

  static HighlightInfo checkClassDoesNotCallSuperConstructorOrHandleExceptions(@NotNull PsiClass aClass,
                                                                               @Nullable RefCountHolder refCountHolder,
                                                                               @NotNull PsiResolveHelper resolveHelper) {
    if (aClass.isEnum()) return null;
    // check only no-ctr classes. Problem with specific constructor will be highlighted inside it
    if (aClass.getConstructors().length != 0) return null;
    // find no-args base class ctr
    TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    return checkBaseClassDefaultConstructorProblem(aClass, refCountHolder, resolveHelper, textRange, PsiClassType.EMPTY_ARRAY);
  }

  static HighlightInfo checkBaseClassDefaultConstructorProblem(@NotNull PsiClass aClass,
                                                               @Nullable RefCountHolder refCountHolder,
                                                               @NotNull PsiResolveHelper resolveHelper,
                                                               @NotNull TextRange range,
                                                               PsiClassType @NotNull [] handledExceptions) {
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
        .descriptionAndTooltip(JavaErrorBundle.message("ambiguous.method.call", m1, m2))
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

    String description = JavaErrorBundle.message("no.default.constructor.available", HighlightUtil.formatClass(baseClass));

    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description).create();
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createCreateConstructorMatchingSuperFix(aClass));

    return info;
  }

  static HighlightInfo checkMustNotBeLocal(@NotNull PsiClass aClass) {
    IElementType token;
    HighlightingFeature feature;
    if (aClass.isEnum()) {
      token = JavaTokenType.ENUM_KEYWORD;
      feature = HighlightingFeature.LOCAL_ENUMS;
    }
    else if (aClass.isInterface()) {
      token = JavaTokenType.INTERFACE_KEYWORD;
      feature = HighlightingFeature.LOCAL_INTERFACES;
    }
    else {
      return null;
    }
    if (!PsiUtil.isLocalClass(aClass)) return null;
    PsiElement anchor = StreamEx.iterate(aClass.getFirstChild(), Objects::nonNull, PsiElement::getNextSibling)
      .findFirst(e -> e instanceof PsiKeyword && ((PsiKeyword)e).getTokenType().equals(token))
      .orElseThrow(NoSuchElementException::new);
    PsiFile file = aClass.getContainingFile();
    return HighlightUtil.checkFeature(anchor, feature, PsiUtil.getLanguageLevel(file), file);
  }

  static HighlightInfo checkCyclicInheritance(@NotNull PsiClass aClass) {
    PsiClass circularClass = InheritanceUtil.getCircularClass(aClass);
    if (circularClass != null) {
      String description = JavaErrorBundle.message("cyclic.inheritance", HighlightUtil.formatClass(circularClass));
      TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description).create();
    }
    return null;
  }

  static HighlightInfo checkExtendsDuplicate(@NotNull PsiJavaCodeReferenceElement element, @Nullable PsiElement resolved, @NotNull PsiFile containingFile) {
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
      String description = JavaErrorBundle.message("duplicate.class", HighlightUtil.formatClass(aClass));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
    }
    return null;
  }

  static HighlightInfo checkClassAlreadyImported(@NotNull PsiClass aClass, @NotNull PsiElement elementToHighlight) {
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
        String description = JavaErrorBundle.message("class.already.imported", HighlightUtil.formatClass(aClass, false));
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description).create();
      }
    }
    return null;
  }

  static HighlightInfo checkClassExtendsOnlyOneClass(@NotNull PsiReferenceList list) {
    PsiClassType[] referencedTypes = list.getReferencedTypes();
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiClass)) return null;

    PsiClass aClass = (PsiClass)parent;
    if (!aClass.isInterface()
        && referencedTypes.length > 1
        && aClass.getExtendsList() == list) {
      String description = JavaErrorBundle.message("class.cannot.extend.multiple.classes");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).create();
    }

    return null;
  }

  static HighlightInfo checkThingNotAllowedInInterface(@NotNull PsiElement element, @Nullable PsiClass aClass) {
    if (aClass == null || !aClass.isInterface()) return null;
    String description = JavaErrorBundle.message("not.allowed.in.interface");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
  }

  static HighlightInfo checkQualifiedNew(@NotNull PsiNewExpression expression, @Nullable PsiType type, @Nullable PsiClass aClass) {
    PsiExpression qualifier = expression.getQualifier();
    if (qualifier == null) return null;
    if (type instanceof PsiArrayType) {
      String description = JavaErrorBundle.message("invalid.qualified.new");
      HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createRemoveNewQualifierFix(expression, null));
      return info;
    }
    HighlightInfo info = null;
    if (aClass != null) {
      if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
        String description = JavaErrorBundle.message("qualified.new.of.static.class");
        info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
        if (!aClass.isEnum()) {
          QuickFixAction.registerQuickFixActions(info, null, JvmElementActionFactories.createModifierActions(aClass, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, false)));
        }
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createRemoveNewQualifierFix(expression, aClass));
      } else {
        if (aClass instanceof PsiAnonymousClass) {
          final PsiClass baseClass = PsiUtil.resolveClassInType(((PsiAnonymousClass)aClass).getBaseClassType());
          if (baseClass != null && baseClass.isInterface()) {
            info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
              .descriptionAndTooltip(JavaErrorBundle.message("anonymous.class.implements.interface.cannot.have.qualifier")).create();
          }
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createRemoveNewQualifierFix(expression, aClass));
        }
        if (info == null) {
          PsiJavaCodeReferenceElement reference = expression.getClassOrAnonymousClassReference();
          if (reference != null) {
            PsiElement refQualifier = reference.getQualifier();
            if (refQualifier != null) {
              info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refQualifier)
                .descriptionAndTooltip(JavaErrorBundle.message("qualified.class.reference.not.allowed.in.qualified.new"))
                .create();
              QuickFixAction
                .registerQuickFixAction(info, QUICK_FIX_FACTORY.createDeleteFix(refQualifier, QuickFixBundle.message("remove.qualifier.fix")));
            }
          }
        }
      }
    }
    return info;
  }


  /**
   * class c extends foreign.inner {}
   *
   * @param extendRef points to the class in the extends list
   * @param resolved  extendRef resolved
   */
  static HighlightInfo checkClassExtendsForeignInnerClass(@NotNull PsiJavaCodeReferenceElement extendRef, final PsiElement resolved) {
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
      String description = JavaErrorBundle.message("class.name.expected");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(extendRef).descriptionAndTooltip(description).create();
    }
    final HighlightInfo[] infos = new HighlightInfo[1];
    extendRef.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
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
          if (baseClass != null && base.hasModifierProperty(PsiModifier.PRIVATE) && baseClass == containerClass && baseClass.getContainingClass() == null) {
            String description = JavaErrorBundle.message("private.symbol",
                                                         HighlightUtil.formatClass(base),
                                                         HighlightUtil.formatClass(baseClass));
            final HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
              .range(extendRef)
              .descriptionAndTooltip(description)
              .create();

            QuickFixAction.registerQuickFixAction(
              info,
              QUICK_FIX_FACTORY.createModifierListFix(base, PsiModifier.PUBLIC, true, false)
            );
            QuickFixAction.registerQuickFixAction(
              info,
              QUICK_FIX_FACTORY.createModifierListFix(base, PsiModifier.PROTECTED, true, false)
            );

            infos[0] = info;
            return;
          }

          // must be inner class
          if (!PsiUtil.isInnerClass(base)) return;

          if (resolve == resolved && baseClass != null && (!PsiTreeUtil.isAncestor(baseClass, extendRef, true) || aClass.hasModifierProperty(PsiModifier.STATIC)) &&
              !InheritanceUtil.hasEnclosingInstanceInScope(baseClass, extendRef, psiClass -> psiClass != aClass, true) &&
              !qualifiedNewCalledInConstructors(aClass)) {
            String description = JavaErrorBundle.message("no.enclosing.instance.in.scope", HighlightUtil.formatClass(baseClass));
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
  private static boolean qualifiedNewCalledInConstructors(@NotNull PsiClass aClass) {
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) return false;
    for (PsiMethod constructor : constructors) {
      PsiMethodCallExpression methodCallExpression = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
      if (methodCallExpression == null) return false;
      if (JavaPsiConstructorUtil.isChainedConstructorCall(methodCallExpression)) continue;
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

  static HighlightInfo checkCreateInnerClassFromStaticContext(@NotNull PsiNewExpression expression, @Nullable PsiType type, @Nullable PsiClass aClass) {
    if (type == null || type instanceof PsiArrayType || type instanceof PsiPrimitiveType) return null;
    if (aClass == null) return null;
    if (aClass instanceof PsiAnonymousClass) {
      aClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
      if (aClass == null) return null;
    }

    PsiExpression qualifier = expression.getQualifier();
    return checkCreateInnerClassFromStaticContext(expression, qualifier, aClass);
  }

  public static HighlightInfo checkCreateInnerClassFromStaticContext(@NotNull PsiElement element,
                                                                     @Nullable PsiExpression qualifier,
                                                                     @Nullable PsiClass aClass) {
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

  static HighlightInfo checkCreateInnerClassFromStaticContext(@NotNull PsiElement element,
                                                              @Nullable PsiElement placeToSearchEnclosingFrom,
                                                              @Nullable PsiClass aClass) {
    if (aClass == null || !PsiUtil.isInnerClass(aClass)) return null;
    PsiClass outerClass = aClass.getContainingClass();
    if (outerClass == null) return null;

    if (outerClass instanceof PsiSyntheticClass || InheritanceUtil.hasEnclosingInstanceInScope(outerClass, placeToSearchEnclosingFrom, true,
                                                                                               false)) return null;
    return reportIllegalEnclosingUsage(placeToSearchEnclosingFrom, aClass, outerClass, element);
  }

  static HighlightInfo checkSuperQualifierType(@NotNull Project project, @NotNull PsiMethodCallExpression superCall) {
    if (!JavaPsiConstructorUtil.isSuperConstructorCall(superCall)) return null;
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
          PsiClassType outerType = JavaPsiFacade.getElementFactory(project).createType(outerClass);
          return HighlightUtil.checkAssignability(outerType, null, qualifier, qualifier);
        }
      } else {
        String description = JavaErrorBundle.message("not.inner.class", HighlightUtil.formatClass(targetClass));
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description).create();
      }
    }
    return null;
  }

  static HighlightInfo reportIllegalEnclosingUsage(PsiElement place,
                                                   @Nullable PsiClass aClass,
                                                   @Nullable PsiClass outerClass,
                                                   @NotNull PsiElement elementToHighlight) {
    if (outerClass != null && !PsiTreeUtil.isContextAncestor(outerClass, place, false)) {
      String description = JavaErrorBundle.message("is.not.an.enclosing.class", HighlightUtil.formatClass(outerClass));
      HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description).create();
      registerMakeInnerClassStatic(aClass, highlightInfo);
      return highlightInfo;
    }
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, outerClass);
    if (staticParent != null) {
      String element = outerClass == null ? "" : HighlightUtil.formatClass(outerClass) + "." +
                                                 (place instanceof PsiSuperExpression ? PsiKeyword.SUPER : PsiKeyword.THIS);
      String description = JavaErrorBundle.message("cannot.be.referenced.from.static.context", element);
      HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description).create();
      // make context not static or referenced class static
      QuickFixAction.registerQuickFixAction(
        highlightInfo,
        QUICK_FIX_FACTORY.createModifierListFix(staticParent, PsiModifier.STATIC, false, false)
      );
      if (aClass != null && HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, aClass.getModifierList()) == null) {
        QuickFixAction.registerQuickFixAction(
          highlightInfo,
          QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, true, false)
        );
      }
      return highlightInfo;
    }
    return null;
  }

  public static HighlightInfo checkWellFormedRecord(PsiClass psiClass) {
    PsiRecordHeader header = psiClass.getRecordHeader();
    if (!psiClass.isRecord()) {
      if (header != null) {
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(header)
          .descriptionAndTooltip(JavaErrorBundle.message("record.header.regular.class")).create();
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createDeleteFix(header));
        return info;
      }
      return null;
    }
    PsiIdentifier identifier = psiClass.getNameIdentifier();
    if (identifier == null) return null;
    if (header == null) {
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier)
        .descriptionAndTooltip(JavaErrorBundle.message("record.no.header")).create();
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddEmptyRecordHeaderFix(psiClass));
      return info;
    }
    return null;
  }
  
  public static HighlightInfo checkWellFormedSealedInheritor(PsiClass psiClass) {
    if (!psiClass.hasModifierProperty(PsiModifier.SEALED) &&
        !psiClass.hasModifierProperty(PsiModifier.NON_SEALED) &&
        !psiClass.hasModifierProperty(PsiModifier.FINAL)) {
      PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
      if (nameIdentifier == null) return null;
      if (Arrays.stream(psiClass.getSuperTypes()).map(superType -> superType.resolve())
        .anyMatch(superClass -> superClass != null && superClass.hasModifierProperty(PsiModifier.SEALED))) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(nameIdentifier)
          .descriptionAndTooltip(JavaErrorBundle.message("sealed.type.inheritor.expected.modifiers", PsiModifier.SEALED, PsiModifier.NON_SEALED, PsiModifier.FINAL)).create();
      }
    }
    return null;
  }

  public static HighlightInfo checkIllegalInstanceMemberInRecord(PsiMember member) {
    if (!member.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass aClass = member.getContainingClass();
      if (aClass != null && aClass.isRecord()) {
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(member)
          .descriptionAndTooltip(JavaErrorBundle.message(member instanceof PsiClassInitializer ?
                                                         "record.instance.initializer" : "record.instance.field")).create();
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createModifierListFix(member, PsiModifier.STATIC, true, false));
        return info;
      }
    }
    return null;
  }

  static HighlightInfo checkExtendsProhibitedClass(@NotNull PsiClass superClass, @NotNull PsiElement elementToHighlight) {
    String qualifiedName = superClass.getQualifiedName();
    if (CommonClassNames.JAVA_LANG_ENUM.equals(qualifiedName) || CommonClassNames.JAVA_LANG_RECORD.equals(qualifiedName)) {
      String message = JavaErrorBundle.message("classes.extends.prohibited.super", qualifiedName);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(message).create();
    }
    return null;
  }

  public static HighlightInfo checkAnonymousInheritProhibited(PsiNewExpression expression) {
    PsiAnonymousClass aClass = expression.getAnonymousClass();
    if (aClass != null) {
      PsiClass superClass = aClass.getSuperClass();
      PsiJavaCodeReferenceElement reference = expression.getClassOrAnonymousClassReference();
      if (superClass != null && reference != null) {
        return checkExtendsProhibitedClass(superClass, reference);
      }
    }
    return null;
  }

  static HighlightInfo checkExtendsSealedClass(PsiClass aClass, PsiClass superClass, PsiJavaCodeReferenceElement elementToHighlight) {
    if (superClass.hasModifierProperty(PsiModifier.SEALED)) {
      PsiClassType[] permittedTypes = superClass.getPermitsListTypes();
      if (permittedTypes.length > 0) {
        if (Arrays.stream(permittedTypes).map(permittedType -> permittedType.resolve()).anyMatch(permittedClass -> aClass.equals(permittedClass))) {
          return null;
        }
      }
      else if (JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, superClass)) {
        return null;
      }
      else {
        PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByElement(aClass);
        if (javaModule != null && javaModule == JavaModuleGraphUtil.findDescriptorByElement(superClass)) {
          return null;
        }
      }
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .descriptionAndTooltip(JavaErrorBundle.message("not.allowed.in.sealed.hierarchy", aClass.getName()))
        .range(elementToHighlight).create();
    }
    return null;
  }
}
