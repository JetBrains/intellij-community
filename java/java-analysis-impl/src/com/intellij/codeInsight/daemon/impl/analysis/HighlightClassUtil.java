// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ClassUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.MoveMembersIntoClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.ChangeModifierRequest;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaImplicitClassIndex;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;
import java.util.function.Consumer;

/**
 * Checks and highlights problems with classes.
 * Generates HighlightInfoType.ERROR-only HighlightInfos at PsiClass level.
 */
public final class HighlightClassUtil {
  /**
   * new ref(...) or new ref(..) { ... } where ref is abstract class
   */
  static HighlightInfo.Builder checkAbstractInstantiation(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiAnonymousClass aClass
        && parent.getParent() instanceof PsiNewExpression
        && !PsiUtilCore.hasErrorElementChild(parent.getParent())) {
      return checkClassWithAbstractMethods(aClass, aClass, ref.getTextRange());
    }
    return null;
  }

  static HighlightInfo.Builder checkClassWithAbstractMethods(@NotNull PsiClass aClass, @NotNull PsiElement implementsFixElement, @NotNull TextRange range) {
    PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(aClass);
    if (abstractMethod == null) {
      return null;
    }

    PsiClass containingClass = abstractMethod.getContainingClass();
    if (containingClass == null ||
        containingClass == aClass ||
        implementsFixElement instanceof PsiEnumConstant && !hasEnumConstantsWithInitializer(aClass)) {
      return null;
    }

    @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE)
    final String messageKey;
    final String referenceName;
    if (aClass instanceof PsiEnumConstantInitializer enumConstant) {
      messageKey = "enum.constant.must.implement.method";
      referenceName = enumConstant.getEnumConstant().getName();
    }
    else {
      messageKey = aClass.isEnum() || aClass.isRecord() || aClass instanceof PsiAnonymousClass
                   ? "class.must.implement.method"
                   : "class.must.be.abstract";
      referenceName = HighlightUtil.formatClass(aClass, false);
    }

    String message = JavaErrorBundle.message(messageKey,
                                             referenceName,
                                             JavaHighlightUtil.formatMethod(abstractMethod),
                                             HighlightUtil.formatClass(containingClass, false));
    HighlightInfo.Builder errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message);
    PsiMethod anyMethodToImplement = ClassUtil.getAnyMethodToImplement(aClass);
    if (anyMethodToImplement != null) {
      if (!anyMethodToImplement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) ||
          JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, containingClass)) {
        IntentionAction action = QuickFixFactory.getInstance().createImplementMethodsFix(implementsFixElement);
        errorResult.registerFix(action, null, null, null, null);
      }
      else {
        QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions(anyMethodToImplement, MemberRequestsKt.modifierRequest(JvmModifier.PROTECTED, true)));
        QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions(anyMethodToImplement, MemberRequestsKt.modifierRequest(JvmModifier.PUBLIC, true)));
      }
    }
    if (!(aClass instanceof PsiAnonymousClass) &&
        !aClass.isEnum()
        && aClass.getModifierList() != null
        && HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, aClass.getModifierList()) == null) {
      IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(aClass, PsiModifier.ABSTRACT, true, false);
      errorResult.registerFix(action, null, null, null, null);
    }
    return errorResult;
  }

  static HighlightInfo.Builder checkClassMustBeAbstract(@NotNull PsiClass aClass, @NotNull TextRange textRange) {
    if (aClass.isEnum()) {
      if (hasEnumConstantsWithInitializer(aClass)) return null;
    }
    else if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) || aClass.getRBrace() == null) {
      return null;
    }
    return checkClassWithAbstractMethods(aClass, aClass, textRange);
  }

  static HighlightInfo.Builder checkInstantiationOfAbstractClass(@NotNull PsiClass aClass, @NotNull PsiElement highlightElement) {
    HighlightInfo.Builder errorResult = null;
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
        (!(highlightElement instanceof PsiNewExpression) || !(((PsiNewExpression)highlightElement).getType() instanceof PsiArrayType))) {
      String baseClassName = aClass.getName();
      PsiMethod anyAbstractMethod = ClassUtil.getAnyAbstractMethod(aClass);
      String message;
      if (aClass.isEnum()) {
        if (anyAbstractMethod == null || anyAbstractMethod.getContainingClass() != aClass) return null;
        message = JavaErrorBundle.message("enum.constant.must.implement.method", highlightElement.getText(),
                                          JavaHighlightUtil.formatMethod(anyAbstractMethod), baseClassName);
      }
      else {
        message = JavaErrorBundle.message("abstract.cannot.be.instantiated", baseClassName);
      }
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(highlightElement).descriptionAndTooltip(message);
      if (!aClass.isInterface() && anyAbstractMethod == null) {
        // suggest to make not abstract only if possible
        QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions(aClass, MemberRequestsKt.modifierRequest(JvmModifier.ABSTRACT, false)));
      }
      if (anyAbstractMethod != null && highlightElement instanceof PsiNewExpression && ((PsiNewExpression)highlightElement).getClassReference() != null) {
        IntentionAction action = QuickFixFactory.getInstance().createImplementAbstractClassMethodsFix(highlightElement);
        errorResult.registerFix(action, null, null, null, null);
      }
    }
    return errorResult;
  }

  private static boolean hasEnumConstantsWithInitializer(@NotNull PsiClass aClass) {
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

  static HighlightInfo.Builder checkDuplicateTopLevelClass(@NotNull PsiClass aClass) {
    if (aClass instanceof PsiImplicitClass) return null; //check in HighlightImplicitClassUtil
    if (!(aClass.getParent() instanceof PsiFile)) return null;
    String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) return null;
    int numOfClassesToFind = 2;
    if (qualifiedName.contains("$")) {
      qualifiedName = qualifiedName.replace('$', '.');
      numOfClassesToFind = 1;
    }

    Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
    if (module == null) return null;

    GlobalSearchScope scope = GlobalSearchScope.moduleScope(module).intersectWith(aClass.getResolveScope());
    PsiClass[] classes = JavaPsiFacade.getInstance(aClass.getProject()).findClasses(qualifiedName, scope);
    if (aClass.getContainingFile() instanceof PsiJavaFile javaFile && javaFile.getPackageStatement() == null) {
      Collection<? extends PsiClass> implicitClasses =
        JavaImplicitClassIndex.getInstance().getElements(qualifiedName, javaFile.getProject(), scope);
      if (!implicitClasses.isEmpty()) {
        ArrayList<PsiClass> newClasses = new ArrayList<>();
        ContainerUtil.addAll(newClasses, classes);
        ContainerUtil.addAll(newClasses, implicitClasses);
        classes = newClasses.toArray(PsiClass.EMPTY_ARRAY);
      }
    }
    if (classes.length < numOfClassesToFind) return null;
    return checkDuplicateClasses(aClass, classes);
  }

  @Nullable
  static HighlightInfo.Builder checkDuplicateClasses(@NotNull PsiClass aClass,  @NotNull PsiClass @NotNull[] classes) {
    PsiManager manager = aClass.getManager();
    Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
    if (module == null) return null;
    ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(aClass);
    if (virtualFile == null) return null;
    boolean isTestSourceRoot = fileIndex.isInTestSourceContent(virtualFile);
    String dupFileName = null;
    PsiClass dupClass = null;
    for (PsiClass dupClassCandidate : classes) {
      // do not use equals
      if (dupClassCandidate != aClass) {
        VirtualFile file = dupClassCandidate.getContainingFile().getVirtualFile();
        if (file != null && manager.isInProject(dupClassCandidate) && fileIndex.isInTestSourceContent(file) == isTestSourceRoot) {
          dupClass = dupClassCandidate;
          dupFileName = FileUtil.toSystemDependentName(file.getPath());
          break;
        }
      }
    }
    if (dupFileName == null) return null;
    HighlightInfo.Builder info = createInfoAndRegisterRenameFix(aClass, dupFileName, "duplicate.class.in.other.file");
    IntentionAction action = QuickFixFactory.getInstance().createNavigateToDuplicateElementFix(dupClass);
    if (info != null) {
      info.registerFix(action, null, null, null, null);
    }
    return info;
  }

  static HighlightInfo.Builder checkDuplicateNestedClass(@NotNull PsiClass aClass) {
    String name = aClass.getName();
    if (name == null) {
      return null;
    }
    PsiElement parent = aClass.getParent();
    boolean checkSiblings;
    if (parent instanceof PsiClass && !PsiUtil.isLocalOrAnonymousClass((PsiClass)parent) && !PsiUtil.isLocalOrAnonymousClass(aClass)) {
      // optimization: instead of iterating PsiClass children manually we can get'em all from caches
      PsiClass innerClass = ((PsiClass)parent).findInnerClassByName(name, false);
      if (innerClass != null && innerClass != aClass) {
        if (innerClass.getTextOffset() > aClass.getTextOffset()) {
          // report duplicate lower in text
          PsiClass c = innerClass;innerClass=aClass;aClass=c;
        }
        HighlightInfo.Builder info = createInfoAndRegisterRenameFix(aClass, name, "duplicate.class");
        IntentionAction action = QuickFixFactory.getInstance().createNavigateToDuplicateElementFix(innerClass);
        if (info != null) {
          info.registerFix(action, null, null, null, null);
        }
        return info;
      }
      checkSiblings = false; // there still might be duplicates in parents
    }
    else {
      checkSiblings = true;
    }
    if (!(parent instanceof PsiDeclarationStatement)) {
      parent = aClass;
    }
    while (parent != null) {
      if (parent instanceof PsiFile) break;
      PsiElement element = checkSiblings ? parent.getPrevSibling() : null;
      if (element == null) {
        element = parent.getParent();
        // JLS 14.3:
        // The name of a local class C may not be redeclared
        // as a local class of the directly enclosing method, constructor, or initializer block within the scope of C, or a compile-time
        // error occurs. However, a local class declaration may be shadowed (6.3.1)
        // anywhere inside a class declaration nested within the local class declaration's scope.
        if (element instanceof PsiMethod ||
            element instanceof PsiClass ||
            element instanceof PsiCodeBlock && element.getParent() instanceof PsiClassInitializer) {
          checkSiblings = false;
        }
      }
      parent = element;

      if (element instanceof PsiDeclarationStatement) element = PsiTreeUtil.getChildOfType(element, PsiClass.class);
      if (element instanceof PsiClass && name.equals(((PsiClass)element).getName())) {
        HighlightInfo.Builder info = createInfoAndRegisterRenameFix(aClass, name, "duplicate.class");
        IntentionAction action = QuickFixFactory.getInstance().createNavigateToDuplicateElementFix((PsiClass)element);
        if (info != null) {
          info.registerFix(action, null, null, null, null);
        }
        return info;
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkPublicClassInRightFile(@NotNull PsiClass aClass) {
    PsiFile containingFile = aClass.getContainingFile();
    if (aClass.getParent() != containingFile || !aClass.hasModifierProperty(PsiModifier.PUBLIC) || !(containingFile instanceof PsiJavaFile file))
      return null;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null || virtualFile.getNameWithoutExtension().equals(aClass.getName())) {
      return null;
    }
    if (JavaHighlightUtil.isJavaHashBangScript(file)) return null;
    String message = JavaErrorBundle.message("public.class.should.be.named.after.file", aClass.getName());
    TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    HighlightInfo.Builder errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).
      range(aClass, range.getStartOffset(), range.getEndOffset()).
      descriptionAndTooltip(message);
    PsiClass[] classes = file.getClasses();
    boolean containsClassForFile = ContainerUtil.exists(classes, otherClass ->
      !otherClass.getManager().areElementsEquivalent(otherClass, aClass) &&
      otherClass.hasModifierProperty(PsiModifier.PUBLIC) &&
      virtualFile.getNameWithoutExtension().equals(otherClass.getName()));
    if (!containsClassForFile) {
      IntentionAction action = QuickFixFactory.getInstance().createRenameFileFix(aClass.getName() + JavaFileType.DOT_DEFAULT_EXTENSION);
      errorResult.registerFix(action, null, null, null, null);
    }
    if (classes.length > 1) {
      IntentionAction action = QuickFixFactory.getInstance().createMoveClassToSeparateFileFix(aClass);
      errorResult.registerFix(action, null, null, null, null);
    }
    IntentionAction action1 = QuickFixFactory.getInstance().createModifierListFix(aClass, PsiModifier.PUBLIC, false, false);
    errorResult.registerFix(action1, null, null, null, null);
    if (!containsClassForFile) {
      IntentionAction action = QuickFixFactory.getInstance().createRenameElementFix(aClass);
      errorResult.registerFix(action, null, null, null, null);
    }
    return errorResult;
  }

  static HighlightInfo.Builder checkClassRestrictedKeyword(@NotNull LanguageLevel level, @NotNull PsiIdentifier identifier) {
    String className = identifier.getText();
    if (isRestrictedIdentifier(className, level)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .descriptionAndTooltip(JavaErrorBundle.message("restricted.identifier", className))
        .range(identifier)
        ;
    }
    return null;
  }

  /**
   * @param typeName name of the type to test
   * @param level language level
   * @return true if given name cannot be used as a type name at given language level
   */
  public static boolean isRestrictedIdentifier(@Nullable String typeName, @NotNull LanguageLevel level) {
    return PsiKeyword.VAR.equals(typeName) && JavaFeature.LVTI.isSufficient(level) ||
           PsiKeyword.YIELD.equals(typeName) && JavaFeature.SWITCH_EXPRESSION.isSufficient(level) ||
           PsiKeyword.RECORD.equals(typeName) && JavaFeature.RECORDS.isSufficient(level) ||
           (PsiKeyword.SEALED.equals(typeName) || PsiKeyword.PERMITS.equals(typeName)) && JavaFeature.SEALED_CLASSES.isSufficient(level);
  }

  static HighlightInfo.Builder checkClassAndPackageConflict(@NotNull PsiClass aClass) {
    String name = aClass.getQualifiedName();
    if (name == null) return null;
    if (CommonClassNames.DEFAULT_PACKAGE.equals(name)) {
      return createInfoAndRegisterRenameFix(aClass, name, "class.clashes.with.package");
    }

    PsiElement file = aClass.getParent();
    if (file instanceof PsiJavaFile && !((PsiJavaFile)file).getPackageName().isEmpty()) {
      PsiElement directory = file.getParent();
      if (directory != null) {
        String simpleName = aClass.getName();
        PsiDirectory subDirectory = simpleName == null ? null : ((PsiDirectory)directory).findSubdirectory(simpleName);
        if (subDirectory != null && simpleName.equals(subDirectory.getName()) && PsiTreeUtil.findChildOfType(subDirectory, PsiJavaFile.class) != null) {
          return createInfoAndRegisterRenameFix(aClass, name, "class.clashes.with.package");
        }
      }
    }

    return null;
  }

  @Nullable
  private static HighlightInfo.Builder createInfoAndRegisterRenameFix(@NotNull PsiClass aClass,
                                                              @NotNull String name,
                                                              @NotNull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String key) {
    String message = JavaErrorBundle.message(key, name);
    PsiIdentifier identifier = aClass.getNameIdentifier();
    HighlightInfo.Builder info;
    if (aClass instanceof PsiImplicitClass) {
      info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(aClass)
        .fileLevelAnnotation()
        .description(message);
      IntentionAction action = QuickFixFactory.getInstance().createRenameFix(aClass);
      if (action != null) {
        info.registerFix(action, null, null, null, null);
      }
    }
    else {
      if (identifier == null) return null;
      TextRange textRange = identifier.getTextRange();
      info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(textRange)
        .descriptionAndTooltip(message);
      IntentionAction action = QuickFixFactory.getInstance().createRenameFix(identifier);
      if (action != null) {
        info.registerFix(action, null, null, null, null);
      }
    }
    return info;
  }

  private static HighlightInfo.Builder checkStaticFieldDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiField.class) == null) {
      return null;
    }

    PsiField field = (PsiField)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(field) || PsiUtil.isCompileTimeConstant(field)) {
      return null;
    }

    HighlightInfo.Builder result = HighlightUtil.checkFeature(keyword, JavaFeature.INNER_STATICS,
                                                              PsiUtil.getLanguageLevel(field), field.getContainingFile());

    IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(field, PsiModifier.STATIC, false, false);
    if (result != null) {
      result.registerFix(action, null, null, null, null);
    }
    registerMakeInnerClassStatic(field.getContainingClass(), result);

    return result;
  }

  private static void registerMakeInnerClassStatic(@Nullable PsiClass aClass, @Nullable HighlightInfo.Builder result) {
    if (aClass != null && aClass.getContainingClass() != null) {
      IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(aClass, PsiModifier.STATIC, true, false);
      if (result != null) {
        result.registerFix(action, null, null, null, null);
      }
    }
  }

  private static HighlightInfo.Builder checkStaticMethodDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiMethod.class) == null) {
      return null;
    }
    PsiMethod method = (PsiMethod)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(method)) return null;
    HighlightInfo.Builder result = HighlightUtil.checkFeature(keyword, JavaFeature.INNER_STATICS,
                                                              PsiUtil.getLanguageLevel(method), method.getContainingFile());
    IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(method, PsiModifier.STATIC, false, false);
    if (result != null) {
      result.registerFix(action, null, null, null, null);
    }
    registerMakeInnerClassStatic((PsiClass)method.getParent(), result);
    return result;
  }

  private static HighlightInfo.Builder checkStaticInitializerDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiClassInitializer.class) == null) {
      return null;
    }
    PsiClassInitializer initializer = (PsiClassInitializer)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(initializer)) return null;
    HighlightInfo.Builder result = HighlightUtil.checkFeature(keyword, JavaFeature.INNER_STATICS,
                                                              PsiUtil.getLanguageLevel(initializer), initializer.getContainingFile());
    IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(initializer, PsiModifier.STATIC, false, false);
    if (result != null) {
      result.registerFix(action, null, null, null, null);
    }
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

  private static HighlightInfo.Builder checkStaticClassDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
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

    TextRange range = context == null ? HighlightNamesUtil.getClassDeclarationTextRange(aClass) : context.getTextRange();
    HighlightInfo.Builder info = HighlightUtil.checkFeature(range, JavaFeature.INNER_STATICS,
                                                            PsiUtil.getLanguageLevel(aClass), aClass.getContainingFile());
    if (context != keyword) {
      QuickFixAction.registerQuickFixActions(info, null, JvmElementActionFactories
        .createModifierActions(aClass, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, false)));
    }
    PsiClass containingClass = aClass.getContainingClass();
    registerMakeInnerClassStatic(containingClass, info);
    return info;
  }

  static HighlightInfo.Builder checkStaticDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    HighlightInfo.Builder errorResult = checkStaticFieldDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    errorResult = checkStaticMethodDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    errorResult = checkStaticClassDeclarationInInnerClass(keyword);
    if (errorResult != null) return errorResult;
    errorResult = checkStaticInitializerDeclarationInInnerClass(keyword);
    return errorResult;
  }

  static HighlightInfo.Builder checkExtendsAllowed(@NotNull PsiReferenceList list) {
    if (list.getParent() instanceof PsiClass aClass && (aClass.isEnum() || aClass.isRecord())) {
      boolean isExtends = list.equals(aClass.getExtendsList());
      if (isExtends) {
        String description = JavaErrorBundle.message(aClass.isRecord() ? "record.extends" : "extends.after.enum");
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list.getFirstChild()).descriptionAndTooltip(description);
        IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(list);
        info.registerFix(action, null, null, null, null);
        return info;
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkImplementsAllowed(@NotNull PsiReferenceList list) {
    if (list.getParent() instanceof PsiClass aClass && aClass.isInterface()) {
      boolean isImplements = list.equals(aClass.getImplementsList());
      if (isImplements) {
        String description = JavaErrorBundle.message("implements.after.interface");
        HighlightInfo.Builder result =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list.getFirstChild()).descriptionAndTooltip(description);
        PsiClassType[] referencedTypes = list.getReferencedTypes();
        if (referencedTypes.length > 0) {
          IntentionAction action = QuickFixFactory.getInstance().createChangeExtendsToImplementsFix(aClass, referencedTypes[0]);
          result.registerFix(action, null, null, null, null);
        }
        return result;
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkExtendsClassAndImplementsInterface(@NotNull PsiReferenceList referenceList,
                                                               @NotNull JavaResolveResult resolveResult,
                                                               @NotNull PsiJavaCodeReferenceElement ref) {
    PsiClass aClass = (PsiClass)referenceList.getParent();
    boolean isImplements = referenceList.equals(aClass.getImplementsList());
    boolean isInterface = aClass.isInterface();
    if (isInterface && isImplements) return null;
    boolean mustBeInterface = isImplements || isInterface;
    HighlightInfo.Builder errorResult = null;
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (extendFrom != null && extendFrom.isInterface() != mustBeInterface) {
      String message = JavaErrorBundle.message(mustBeInterface ? "interface.expected" : "no.interface.expected");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(message);
      PsiClassType type =
        JavaPsiFacade.getElementFactory(aClass.getProject()).createType(ref);
      IntentionAction action = QuickFixFactory.getInstance().createChangeExtendsToImplementsFix(aClass, type);
      errorResult.registerFix(action, null, null, null, null);
    }
    return errorResult;
  }

  static HighlightInfo.Builder checkCannotInheritFromFinal(@NotNull PsiClass superClass, @NotNull PsiElement elementToHighlight) {
    HighlightInfo.Builder errorResult = null;
    if (superClass.hasModifierProperty(PsiModifier.FINAL) || superClass.isEnum()) {
      String message = JavaErrorBundle.message("inheritance.from.final.class", HighlightUtil.formatClass(superClass),
                                               superClass.isEnum() ? PsiKeyword.ENUM : PsiKeyword.FINAL);
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(message);
      ChangeModifierRequest removeFinal = MemberRequestsKt.modifierRequest(JvmModifier.FINAL, false);
      QuickFixAction.registerQuickFixActions(errorResult, null, JvmElementActionFactories.createModifierActions(superClass, removeFinal));
    }
    return errorResult;
  }

  static HighlightInfo.Builder checkAnonymousInheritFinal(@NotNull PsiNewExpression expression) {
    PsiAnonymousClass aClass = PsiTreeUtil.getChildOfType(expression, PsiAnonymousClass.class);
    if (aClass == null) return null;
    PsiClassType baseClassReference = aClass.getBaseClassType();
    PsiClass baseClass = baseClassReference.resolve();
    if (baseClass == null) return null;
    return checkCannotInheritFromFinal(baseClass, aClass.getBaseClassReference());
  }

  private static @NlsContexts.DetailedDescription String checkDefaultConstructorThrowsException(@NotNull PsiMethod constructor, PsiClassType @NotNull [] handledExceptions) {
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

  static HighlightInfo.Builder checkClassDoesNotCallSuperConstructorOrHandleExceptions(@NotNull PsiClass aClass,
                                                                                       @NotNull PsiResolveHelper resolveHelper) {
    if (aClass.isEnum()) return null;
    // check only no-ctr classes. Problem with specific constructor will be highlighted inside it
    if (aClass.getConstructors().length != 0) return null;
    // find no-args base class ctr
    TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    return checkBaseClassDefaultConstructorProblem(aClass, resolveHelper, textRange, PsiClassType.EMPTY_ARRAY);
  }

  static HighlightInfo.Builder checkBaseClassDefaultConstructorProblem(@NotNull PsiClass aClass,
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
      .limit(2).toList();

    if (constructorCandidates.size() >= 2) {// two ambiguous var-args-only constructors
      String m1 = PsiFormatUtil.formatMethod(constructorCandidates.get(0), PsiSubstitutor.EMPTY,
                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                             PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_PARAMETERS,
                                             PsiFormatUtilBase.SHOW_TYPE);
      String m2 = PsiFormatUtil.formatMethod(constructorCandidates.get(1), PsiSubstitutor.EMPTY,
                                             PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                             PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_PARAMETERS,
                                             PsiFormatUtilBase.SHOW_TYPE);
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(range)
        .descriptionAndTooltip(JavaErrorBundle.message("ambiguous.method.call", m1, m2));

      IntentionAction action1 = QuickFixFactory.getInstance().createCreateConstructorMatchingSuperFix(aClass);
      info.registerFix(action1, null, null, null, null);
      IntentionAction action = QuickFixFactory.getInstance().createAddDefaultConstructorFix(baseClass);
      info.registerFix(action, null, null, null, null);
      return info;
    }

    if (!constructorCandidates.isEmpty()) {
      PsiMethod constructor = constructorCandidates.get(0);
      String description = checkDefaultConstructorThrowsException(constructor, handledExceptions);
      if (description != null) {
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description);
        IntentionAction action = QuickFixFactory.getInstance().createCreateConstructorMatchingSuperFix(aClass);
        info.registerFix(action, null, null, null, null);
        return info;
      }
      return null;
    }

    // no need to distract with missing constructor error when there is already a "Cannot inherit from final class" error message
    if (baseClass.hasModifierProperty(PsiModifier.FINAL)) return null;

    String description = JavaErrorBundle.message("no.default.constructor.available", HighlightUtil.formatClass(baseClass));
    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description);
    IntentionAction action = QuickFixFactory.getInstance().createCreateConstructorMatchingSuperFix(aClass);
    info.registerFix(action, null, null, null, null);

    return info;
  }

  static HighlightInfo.Builder checkMustNotBeLocal(@NotNull PsiClass aClass) {
    IElementType token;
    JavaFeature feature;
    if (aClass.isEnum()) {
      token = JavaTokenType.ENUM_KEYWORD;
      feature = JavaFeature.LOCAL_ENUMS;
    }
    else if (aClass.isInterface()) {
      token = JavaTokenType.INTERFACE_KEYWORD;
      feature = aClass.isAnnotationType() ? null : JavaFeature.LOCAL_INTERFACES;
    }
    else {
      return null;
    }
    if (!PsiUtil.isLocalClass(aClass)) return null;
    PsiElement anchor = StreamEx.iterate(aClass.getFirstChild(), Objects::nonNull, PsiElement::getNextSibling)
      .findFirst(e -> e instanceof PsiKeyword && ((PsiKeyword)e).getTokenType().equals(token))
      .orElseThrow(NoSuchElementException::new);
    PsiFile file = aClass.getContainingFile();
    if (feature == null) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(anchor)
        .descriptionAndTooltip(JavaErrorBundle.message("annotation.cannot.be.local"))
        ;
    }
    return HighlightUtil.checkFeature(anchor, feature, PsiUtil.getLanguageLevel(file), file);
  }

  static HighlightInfo.Builder checkCyclicInheritance(@NotNull PsiClass aClass) {
    PsiClass circularClass = InheritanceUtil.getCircularClass(aClass);
    if (circularClass != null) {
      String description = JavaErrorBundle.message("cyclic.inheritance", HighlightUtil.formatClass(circularClass));
      TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description);
    }
    return null;
  }

  static HighlightInfo.Builder checkExtendsDuplicate(@NotNull PsiJavaCodeReferenceElement element, 
                                                     @Nullable PsiElement resolved, 
                                                     @NotNull PsiFile containingFile) {
    if (!(element.getParent() instanceof PsiReferenceList list)) return null;
    if (!(list.getParent() instanceof PsiClass)) return null;
    if (!(resolved instanceof PsiClass aClass)) return null;
    PsiManager manager = containingFile.getManager();
    PsiJavaCodeReferenceElement sibling = PsiTreeUtil.getPrevSiblingOfType(element, PsiJavaCodeReferenceElement.class);
    while (true) {
      if (sibling == null) return null;
      PsiElement target = sibling.resolve();
      if (manager.areElementsEquivalent(target, aClass)) break;
      sibling = PsiTreeUtil.getPrevSiblingOfType(sibling, PsiJavaCodeReferenceElement.class);
    }
    String name = HighlightUtil.formatClass(aClass);
    String description = JavaErrorBundle.message("duplicate.reference.in.list", name, list.getFirstChild().getText());
    HighlightInfo.Builder info =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description);
    IntentionAction action = QuickFixFactory.getInstance().createRemoveDuplicateExtendsAction(name);
    info.registerFix(action, null, null, null, null);
    return info;
  }

  static HighlightInfo.Builder checkClassAlreadyImported(@NotNull PsiClass aClass, @NotNull PsiElement elementToHighlight) {
    PsiFile file = aClass.getContainingFile();
    if (!(file instanceof PsiJavaFile javaFile)) return null;
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
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description);
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkClassExtendsOnlyOneClass(@NotNull PsiReferenceList list) {
    PsiClassType[] referencedTypes = list.getReferencedTypes();
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiClass aClass)) return null;

    if (!aClass.isInterface()
        && referencedTypes.length > 1
        && aClass.getExtendsList() == list) {
      String description = JavaErrorBundle.message("class.cannot.extend.multiple.classes");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description);
    }

    return null;
  }

  static HighlightInfo.Builder checkThingNotAllowedInInterface(@NotNull PsiElement element, @Nullable PsiClass aClass) {
    if (aClass == null || !aClass.isInterface()) return null;
    String description = JavaErrorBundle.message("not.allowed.in.interface");
    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description);
    IntentionAction action1 = QuickFixFactory.getInstance().createDeleteFix(element);
    info.registerFix(action1, null, null, null, null);
    IntentionAction action = QuickFixFactory.getInstance().createConvertInterfaceToClassFix(aClass);
    info.registerFix(action, null, null, null, null);
    return info;
  }

  static HighlightInfo.Builder checkQualifiedNew(@NotNull PsiNewExpression expression, @Nullable PsiType type, @Nullable PsiClass aClass) {
    PsiExpression qualifier = expression.getQualifier();
    if (qualifier == null) return null;
    if (type instanceof PsiArrayType) {
      String description = JavaErrorBundle.message("invalid.qualified.new");
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
      IntentionAction action = QuickFixFactory.getInstance().createRemoveNewQualifierFix(expression, null);
      info.registerFix(action, null, null, null, null);
      return info;
    }
    HighlightInfo.Builder info = null;
    if (aClass != null) {
      if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
        String description = JavaErrorBundle.message("qualified.new.of.static.class");
        info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
        if (!aClass.isEnum()) {
          QuickFixAction.registerQuickFixActions(info, null, JvmElementActionFactories.createModifierActions(aClass,
                                                                                                             MemberRequestsKt.modifierRequest(
                                                                                                               JvmModifier.STATIC, false)));
        }
        IntentionAction action = QuickFixFactory.getInstance().createRemoveNewQualifierFix(expression, aClass);
        info.registerFix(action, null, null, null, null);
      } else {
        if (aClass instanceof PsiAnonymousClass) {
          PsiClass baseClass = PsiUtil.resolveClassInType(((PsiAnonymousClass)aClass).getBaseClassType());
          if (baseClass != null && baseClass.isInterface()) {
            info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
              .descriptionAndTooltip(JavaErrorBundle.message("anonymous.class.implements.interface.cannot.have.qualifier"));
          }
          IntentionAction action = QuickFixFactory.getInstance().createRemoveNewQualifierFix(expression, aClass);
          if (info != null) {
            info.registerFix(action, null, null, null, null);
          }
        }
        if (info == null) {
          PsiJavaCodeReferenceElement reference = expression.getClassOrAnonymousClassReference();
          if (reference != null) {
            PsiElement refQualifier = reference.getQualifier();
            if (refQualifier != null) {
              info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refQualifier)
                .descriptionAndTooltip(JavaErrorBundle.message("qualified.class.reference.not.allowed.in.qualified.new"))
              ;
              IntentionAction action =
                QuickFixFactory.getInstance().createDeleteFix(refQualifier, QuickFixBundle.message("remove.qualifier.fix"));
              info.registerFix(action, null, null, null, null);
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
  static HighlightInfo.Builder checkClassExtendsForeignInnerClass(@NotNull PsiJavaCodeReferenceElement extendRef, @Nullable PsiElement resolved) {
    PsiElement parent = extendRef.getParent();
    if (!(parent instanceof PsiReferenceList)) {
      return null;
    }
    PsiElement grand = parent.getParent();
    if (!(grand instanceof PsiClass aClass)) {
      return null;
    }
    PsiClass containerClass;
    if (aClass instanceof PsiTypeParameter typeParameter) {
      if (!(typeParameter.getOwner() instanceof PsiClass cls)) {
        return null;
      }
      containerClass = cls;
    }
    else {
      containerClass = aClass;
    }
    if (aClass.getExtendsList() != parent && aClass.getImplementsList() != parent) {
      return null;
    }
    if (resolved != null && !(resolved instanceof PsiClass)) {
      String description = JavaErrorBundle.message("class.name.expected");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(extendRef).descriptionAndTooltip(description);
    }
    HighlightInfo.Builder[] infos = new HighlightInfo.Builder[1];
    extendRef.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (infos[0] != null) return;
        super.visitElement(element);
      }

      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiClass base) {
          PsiClass baseClass = base.getContainingClass();
          if (baseClass != null && base.hasModifierProperty(PsiModifier.PRIVATE) && baseClass == containerClass && baseClass.getContainingClass() == null) {
            String description = JavaErrorBundle.message("private.symbol",
                                                         HighlightUtil.formatClass(base),
                                                         HighlightUtil.formatClass(baseClass));
            HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
              .range(extendRef)
              .descriptionAndTooltip(description);

            IntentionAction action1 = QuickFixFactory.getInstance().createModifierListFix(base, PsiModifier.PUBLIC, true, false);
            info.registerFix(action1, null, null, null, null);
            IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(base, PsiModifier.PROTECTED, true, false);
            info.registerFix(action, null, null, null, null);

            infos[0] = info;
            return;
          }

          // must be inner class
          if (!PsiUtil.isInnerClass(base)) return;

          if (resolve == resolved && baseClass != null && (!PsiTreeUtil.isAncestor(baseClass, extendRef, true) || aClass.hasModifierProperty(PsiModifier.STATIC)) &&
              !InheritanceUtil.hasEnclosingInstanceInScope(baseClass, extendRef, psiClass -> psiClass != aClass, true) &&
              !qualifiedNewCalledInConstructors(aClass)) {
            String description = JavaErrorBundle.message("no.enclosing.instance.in.scope", HighlightUtil.formatClass(baseClass));
            infos[0] = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(extendRef).descriptionAndTooltip(description);
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

  static HighlightInfo.Builder checkCreateInnerClassFromStaticContext(@NotNull PsiNewExpression expression, @NotNull PsiType type, @NotNull PsiClass aClass) {
    if (type instanceof PsiArrayType || type instanceof PsiPrimitiveType) return null;
    if (aClass instanceof PsiAnonymousClass) {
      aClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
      if (aClass == null) return null;
    }

    PsiExpression qualifier = expression.getQualifier();
    return checkCreateInnerClassFromStaticContext(expression, qualifier, aClass);
  }

  public static HighlightInfo.Builder checkCreateInnerClassFromStaticContext(@NotNull PsiElement element,
                                                                     @Nullable PsiExpression qualifier,
                                                                     @NotNull PsiClass aClass) {
    PsiElement placeToSearchEnclosingFrom;
    if (qualifier != null) {
      PsiType qType = qualifier.getType();
      placeToSearchEnclosingFrom = PsiUtil.resolveClassInType(qType);
    }
    else {
      placeToSearchEnclosingFrom = element;
    }
    if (placeToSearchEnclosingFrom == null) {
      return null;
    }
    return checkCreateInnerClassFromStaticContext(element, placeToSearchEnclosingFrom, aClass);
  }

  static HighlightInfo.Builder checkCreateInnerClassFromStaticContext(@NotNull PsiElement element,
                                                              @NotNull PsiElement placeToSearchEnclosingFrom,
                                                              @NotNull PsiClass aClass) {
    if (!PsiUtil.isInnerClass(aClass)) return null;
    PsiClass outerClass = aClass.getContainingClass();
    if (outerClass == null) return null;

    if (outerClass instanceof PsiSyntheticClass ||
        InheritanceUtil.hasEnclosingInstanceInScope(outerClass, placeToSearchEnclosingFrom, true, false)) {
      return null;
    }
    return checkIllegalEnclosingUsage(placeToSearchEnclosingFrom, aClass, outerClass, element);
  }

  static HighlightInfo.Builder checkSuperQualifierType(@NotNull Project project, @NotNull PsiMethodCallExpression superCall) {
    if (!JavaPsiConstructorUtil.isSuperConstructorCall(superCall)) return null;
    PsiMethod ctr = PsiTreeUtil.getParentOfType(superCall, PsiMethod.class, true, PsiMember.class);
    if (ctr == null) return null;
    PsiClass aClass = ctr.getContainingClass();
    if (aClass == null) return null;
    PsiClass targetClass = aClass.getSuperClass();
    if (targetClass == null) return null;
    PsiExpression qualifier = superCall.getMethodExpression().getQualifierExpression();
    if (qualifier != null) {
      if (isRealInnerClass(targetClass)) {
        PsiClass outerClass = targetClass.getContainingClass();
        if (outerClass != null) {
          PsiClassType outerType = JavaPsiFacade.getElementFactory(project).createType(outerClass);
          return HighlightUtil.checkAssignability(outerType, null, qualifier, qualifier);
        }
      } else {
        String description = JavaErrorBundle.message("not.inner.class", HighlightUtil.formatClass(targetClass));
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description);
      }
    }
    return null;
  }

  /** JLS 8.1.3. Inner Classes and Enclosing Instances */
  private static boolean isRealInnerClass(PsiClass aClass) {
    if (PsiUtil.isInnerClass(aClass)) return true;
    if (!PsiUtil.isLocalOrAnonymousClass(aClass)) return false;
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) return false; // check for implicit staticness
    PsiMember member = PsiTreeUtil.getParentOfType(aClass, PsiMember.class, true);
    return member != null && !member.hasModifierProperty(PsiModifier.STATIC);
  }

  static HighlightInfo.Builder checkIllegalEnclosingUsage(@NotNull PsiElement place,
                                                  @Nullable PsiClass aClass,
                                                  @NotNull PsiClass outerClass,
                                                  @NotNull PsiElement elementToHighlight) {
    if (!PsiTreeUtil.isContextAncestor(outerClass, place, false)) {
      String description = JavaErrorBundle.message("is.not.an.enclosing.class", HighlightUtil.formatClass(outerClass));
      HighlightInfo.Builder highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description);
      registerMakeInnerClassStatic(aClass, highlightInfo);
      return highlightInfo;
    }
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, outerClass);
    if (staticParent != null) {
      String element = HighlightUtil.formatClass(outerClass) + "." +
                       (place instanceof PsiSuperExpression ? PsiKeyword.SUPER : PsiKeyword.THIS);
      String description = JavaErrorBundle.message("cannot.be.referenced.from.static.context", element);
      HighlightInfo.Builder builder =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description);
      // make context not static or referenced class static
      IntentionAction action1 = QuickFixFactory.getInstance().createModifierListFix(staticParent, PsiModifier.STATIC, false, false);
      builder.registerFix(action1, null, null, null, null);
      PsiModifierList classModifierList;
      if (aClass != null
          && (classModifierList = aClass.getModifierList()) != null
          && HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, classModifierList) == null) {
        IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(aClass, PsiModifier.STATIC, true, false);
        builder.registerFix(action, null, null, null, null);
      }
      return builder;
    }
    return null;
  }

  static HighlightInfo.Builder checkWellFormedRecord(@NotNull PsiClass psiClass) {
    PsiRecordHeader header = psiClass.getRecordHeader();
    if (!psiClass.isRecord()) {
      if (header != null) {
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(header)
          .descriptionAndTooltip(JavaErrorBundle.message("record.header.regular.class"));
        IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(header);
        info.registerFix(action, null, null, null, null);
        return info;
      }
      return null;
    }
    PsiIdentifier identifier = psiClass.getNameIdentifier();
    if (identifier == null) return null;
    if (header == null) {
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier)
        .descriptionAndTooltip(JavaErrorBundle.message("record.no.header"));
      IntentionAction action = QuickFixFactory.getInstance().createAddEmptyRecordHeaderFix(psiClass);
      info.registerFix(action, null, null, null, null);
      return info;
    }
    return null;
  }

  static HighlightInfo.Builder checkIllegalInstanceMemberInRecord(@NotNull PsiMember member) {
    if (!member.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass aClass = member.getContainingClass();
      if (aClass != null && aClass.isRecord()) {
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(member)
          .descriptionAndTooltip(JavaErrorBundle.message(member instanceof PsiClassInitializer ?
                                                         "record.instance.initializer" : "record.instance.field"));
        IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(member, PsiModifier.STATIC, true, false);
        info.registerFix(action, null, null, null, null);
        return info;
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkExtendsProhibitedClass(@NotNull PsiClass superClass, @NotNull PsiClass psiClass, @NotNull PsiElement elementToHighlight) {
    String qualifiedName = superClass.getQualifiedName();
    if (CommonClassNames.JAVA_LANG_ENUM.equals(qualifiedName) && !psiClass.isEnum() || CommonClassNames.JAVA_LANG_RECORD.equals(qualifiedName) && !psiClass.isRecord()) {
      String message = JavaErrorBundle.message("classes.extends.prohibited.super", qualifiedName);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(message);
    }
    return null;
  }

  static HighlightInfo.Builder checkAnonymousInheritProhibited(@NotNull PsiNewExpression expression) {
    PsiAnonymousClass aClass = expression.getAnonymousClass();
    if (aClass != null) {
      PsiClass superClass = aClass.getSuperClass();
      PsiJavaCodeReferenceElement reference = expression.getClassOrAnonymousClassReference();
      if (superClass != null && reference != null) {
        return checkExtendsProhibitedClass(superClass, aClass, reference);
      }
    }
    return null;
  }
  
  static HighlightInfo.Builder checkExtendsSealedClass(@NotNull PsiFunctionalExpression expression, @NotNull PsiType functionalInterfaceType) {
    PsiClass functionalInterface = PsiUtil.resolveClassInClassTypeOnly(functionalInterfaceType);
    if (functionalInterface == null || !functionalInterface.hasModifierProperty(PsiModifier.SEALED)) return null;
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(expression)
      .descriptionAndTooltip(JavaErrorBundle.message("sealed.cannot.be.functional.interface"))
      ;
  }

   public static HighlightInfo.Builder checkExtendsSealedClass(@NotNull PsiClass aClass,
                                                       @NotNull PsiClass superClass,
                                                       @NotNull PsiJavaCodeReferenceElement elementToHighlight) {
    if (superClass.hasModifierProperty(PsiModifier.SEALED)) {
      if (PsiUtil.isLocalClass(aClass)) {
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(elementToHighlight)
          .descriptionAndTooltip(JavaErrorBundle.message("local.classes.must.not.extend.sealed.classes"));
        IntentionAction action = QuickFixFactory.getInstance().createConvertLocalToInnerAction(aClass);
        info.registerFix(action, null, null, null, null);
        return info;
      }

      PsiClassType[] permittedTypes = superClass.getPermitsListTypes();
      if (permittedTypes.length > 0) {
        PsiManager manager = superClass.getManager();
        if (ContainerUtil.exists(permittedTypes, permittedType -> manager.areElementsEquivalent(aClass, permittedType.resolve()))) {
          return null;
        }
      }
      else if (aClass.getContainingFile() == superClass.getContainingFile()) {
        return null;
      }
      PsiIdentifier identifier = aClass.getNameIdentifier();
      if (identifier == null) {
        return null;
      }
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .descriptionAndTooltip(JavaErrorBundle.message("not.allowed.in.sealed.hierarchy", aClass.getName()))
        .range(elementToHighlight);
      if (!(superClass instanceof PsiCompiledElement)) {
        IntentionAction action = QuickFixFactory.getInstance().createAddToPermitsListFix(aClass, superClass);
        info.registerFix(action, null, null, null, null);
      }
      return info;
    }
    return null;
  }

  static HighlightInfo.Builder checkAnonymousSealedProhibited(@NotNull PsiNewExpression newExpression) {
    PsiAnonymousClass aClass = newExpression.getAnonymousClass();
    if (aClass != null) {
      PsiClass superClass = aClass.getBaseClassType().resolve();
      if (superClass != null && superClass.hasModifierProperty(PsiModifier.SEALED)) {
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(aClass.getBaseClassReference())
          .descriptionAndTooltip(JavaErrorBundle.message("anonymous.classes.must.not.extend.sealed.classes"));
        IntentionAction action = QuickFixFactory.getInstance().createConvertAnonymousToInnerAction(aClass);
        info.registerFix(action, null, null, null, null);
        return info;
      }
    }
    return null;
  }

  static void checkPermitsList(@NotNull PsiReferenceList list, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiClass aClass) || !list.equals(aClass.getPermitsList())) {
      return;
    }
    HighlightInfo.Builder feature = HighlightUtil.checkFeature(list.getFirstChild(), JavaFeature.SEALED_CLASSES,
                                                               PsiUtil.getLanguageLevel(list), list.getContainingFile());
    if (feature != null) {
      errorSink.accept(feature);
      return;
    }
    PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
    if (nameIdentifier == null) return;
    if (aClass.isEnum() || aClass.isRecord() || aClass.isAnnotationType()) {
      String description;
      if (aClass.isEnum()) description = JavaErrorBundle.message("permits.after.enum");
      else if (aClass.isRecord()) description = JavaErrorBundle.message("record.permits");
      else description = JavaErrorBundle.message("annotation.type.permits");
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(list.getFirstChild())
        .descriptionAndTooltip(description);
      IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(list);
      builder.registerFix(action, null, null, null, null);
      errorSink.accept(builder);
      return;
    }
    if (!aClass.hasModifierProperty(PsiModifier.SEALED)) {
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(list.getFirstChild())
        .descriptionAndTooltip(JavaErrorBundle.message("invalid.permits.clause", aClass.getName()));
      IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(aClass, PsiModifier.SEALED, true, false);
      builder.registerFix(action, null, null, null, null);
      errorSink.accept(builder);
    }

    PsiJavaModule currentModule = JavaModuleGraphUtil.findDescriptorByElement(aClass);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(aClass.getProject());
    for (PsiJavaCodeReferenceElement permitted : list.getReferenceElements()) {

      for (PsiAnnotation annotation : PsiTreeUtil.findChildrenOfType(permitted, PsiAnnotation.class)) {
        HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(annotation)
          .descriptionAndTooltip(JavaErrorBundle.message("annotation.not.allowed.in.permit.list"));
        IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(annotation);
        builder.registerFix(action, null, null, null, null);
        errorSink.accept(builder);
      }

      PsiReferenceParameterList parameterList = permitted.getParameterList();
      if (parameterList != null && parameterList.getTypeParameterElements().length > 0) {
        HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameterList)
          .descriptionAndTooltip(JavaErrorBundle.message("permits.list.generics.are.not.allowed"));
        IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(parameterList);
        builder.registerFix(action, null, null, null, null);
        errorSink.accept(builder);
        continue;
      }
      @Nullable PsiElement resolve = permitted.resolve();
      if (resolve instanceof PsiClass inheritorClass) {
        PsiManager manager = inheritorClass.getManager();
        if (!ContainerUtil.exists(inheritorClass.getSuperTypes(), type -> manager.areElementsEquivalent(aClass, type.resolve()))) {
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(permitted)
            .descriptionAndTooltip(JavaErrorBundle.message("invalid.permits.clause.direct.implementation",
                                                           inheritorClass.getName(),
                                                           inheritorClass.isInterface() == aClass.isInterface() ? 1 : 2,
                                                           aClass.getName()));
          QuickFixAction.registerQuickFixActions(info, null,
                                                 QuickFixFactory.getInstance()
                                                   .createExtendSealedClassFixes(permitted, aClass, inheritorClass));
          errorSink.accept(info);
        }
        else {
          if (currentModule == null && !psiFacade.arePackagesTheSame(aClass, inheritorClass)) {
            HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
              .range(permitted)
              .descriptionAndTooltip(JavaErrorBundle.message("class.not.allowed.to.extend.sealed.class.from.another.package"));
            PsiFile parentFile = aClass.getContainingFile();
            if (parentFile instanceof PsiClassOwner) {
              String parentPackage = ((PsiClassOwner)parentFile).getPackageName();
              IntentionAction action = QuickFixFactory.getInstance().createMoveClassToPackageFix(inheritorClass, parentPackage);
              info.registerFix(action, null, null, null, null);
            }
            errorSink.accept(info);
          }
          else if (currentModule != null && !areModulesTheSame(currentModule, JavaModuleGraphUtil.findDescriptorByElement(inheritorClass))) {
            HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
              .range(permitted)
              .descriptionAndTooltip(JavaErrorBundle.message("class.not.allowed.to.extend.sealed.class.from.another.module"));
            errorSink.accept(info);
          }
          else if (!(inheritorClass instanceof PsiCompiledElement) && !hasPermittedSubclassModifier(inheritorClass)) {
            HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
              .range(permitted)
              .descriptionAndTooltip(JavaErrorBundle.message("permitted.subclass.must.have.modifier"));
            IntentionAction markNonSealed = QuickFixFactory.getInstance()
              .createModifierListFix(inheritorClass, PsiModifier.NON_SEALED, true, false);
            info.registerFix(markNonSealed, null, null, null, null);
            boolean hasInheritors = DirectClassInheritorsSearch.search(inheritorClass).findFirst() != null;
            if (!inheritorClass.isInterface() && !inheritorClass.hasModifierProperty(PsiModifier.ABSTRACT) || hasInheritors) {
              IntentionAction action = hasInheritors ?
                                       QuickFixFactory.getInstance().createSealClassFromPermitsListFix(inheritorClass) :
                                       QuickFixFactory.getInstance().createModifierListFix(inheritorClass, PsiModifier.FINAL, true, false);
              info.registerFix(action, null, null, null, null);
            }
            errorSink.accept(info);
          }
        }
      }
    }
  }

  private static boolean areModulesTheSame(@NotNull PsiJavaModule module, PsiJavaModule module1) {
    return module1 != null && module.getOriginalElement() == module1.getOriginalElement();
  }

  static HighlightInfo.Builder checkSealedClassInheritors(@NotNull PsiClass psiClass) {
    if (psiClass.hasModifierProperty(PsiModifier.SEALED)) {
      PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
      if (nameIdentifier == null) return null;
      if (psiClass.isEnum()) return null;

      Collection<PsiClass> inheritors = DirectClassInheritorsSearch.search(psiClass).findAll();
      if (inheritors.isEmpty()) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(nameIdentifier)
          .descriptionAndTooltip(JavaErrorBundle.message("sealed.must.have.inheritors"))
          ;
      }
      PsiFile parentFile = psiClass.getContainingFile();
      PsiManager manager = parentFile.getManager();
      boolean hasOutsideClasses = ContainerUtil.exists(inheritors, inheritor -> !manager.areElementsEquivalent(
        inheritor.getNavigationElement().getContainingFile(), parentFile));
      if (hasOutsideClasses) {
        Map<PsiJavaCodeReferenceElement, PsiClass> permittedClassesRefs = getPermittedClassesRefs(psiClass);
        Collection<PsiClass> permittedClasses = permittedClassesRefs.values();
        boolean hasMissingInheritors = ContainerUtil.exists(inheritors, inheritor -> !permittedClasses.contains(inheritor));
        if (hasMissingInheritors) {
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(nameIdentifier)
            .descriptionAndTooltip(JavaErrorBundle.message("permit.list.must.contain.outside.inheritors"));
          IntentionAction action = QuickFixFactory.getInstance().createFillPermitsListFix(nameIdentifier);
          info.registerFix(action, null, null, null, null);
          return info;
        }
      }
    }
    return null;
  }

  private static @NotNull Map<PsiJavaCodeReferenceElement, PsiClass> getPermittedClassesRefs(@NotNull PsiClass psiClass) {
    PsiReferenceList permitsList = psiClass.getPermitsList();
    if (permitsList == null) return Collections.emptyMap();
    PsiJavaCodeReferenceElement[] classRefs = permitsList.getReferenceElements();
    return ContainerUtil.map2Map(classRefs, r -> Pair.create(r, ObjectUtils.tryCast(r.resolve(), PsiClass.class)));
  }

  private static boolean hasPermittedSubclassModifier(@NotNull PsiClass psiClass) {
    PsiModifierList modifiers = psiClass.getModifierList();
    if (modifiers == null) return false;
    return modifiers.hasModifierProperty(PsiModifier.SEALED) ||
           modifiers.hasModifierProperty(PsiModifier.NON_SEALED) ||
           modifiers.hasModifierProperty(PsiModifier.FINAL);
  }

  static HighlightInfo.Builder checkSealedSuper(@NotNull PsiClass aClass) {
    PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
    if (nameIdentifier != null &&
        !(aClass instanceof PsiTypeParameter) &&
        !aClass.hasModifierProperty(PsiModifier.SEALED) &&
        !aClass.hasModifierProperty(PsiModifier.NON_SEALED) &&
        !aClass.hasModifierProperty(PsiModifier.FINAL) &&
        Arrays.stream(aClass.getSuperTypes())
          .map(type -> type.resolve())
          .anyMatch(superClass -> superClass != null && superClass.hasModifierProperty(PsiModifier.SEALED))) {
      boolean canBeFinal = !aClass.isInterface() && DirectClassInheritorsSearch.search(aClass).findFirst() == null;
      String message =
        canBeFinal
        ? JavaErrorBundle.message("sealed.type.inheritor.expected.modifiers", PsiModifier.SEALED, PsiModifier.NON_SEALED, PsiModifier.FINAL)
        : JavaErrorBundle.message("sealed.type.inheritor.expected.modifiers2", PsiModifier.SEALED, PsiModifier.NON_SEALED);
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(nameIdentifier)
        .descriptionAndTooltip(message);
      if (canBeFinal) {
        IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(aClass, PsiModifier.FINAL, true, false);
        info.registerFix(action, null, null, null, null);
      }
      IntentionAction action1 = QuickFixFactory.getInstance().createModifierListFix(aClass, PsiModifier.SEALED, true, false);
      info.registerFix(action1, null, null, null, null);
      IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(aClass, PsiModifier.NON_SEALED, true, false);
      info.registerFix(action, null, null, null, null);
      return info;
    }
    return null;
  }

  static HighlightInfo.Builder checkShebangComment(@NotNull PsiComment comment) {
    if (comment.getTextOffset() != 0) {
      return null;
    }
    if (comment.getText().startsWith("#!")) {
      VirtualFile file = PsiUtilCore.getVirtualFile(comment);
      if (file != null && "java".equals(file.getExtension())) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .descriptionAndTooltip(JavaAnalysisBundle.message("text.shebang.mechanism.in.java.files.not.permitted"))
          .range(comment, 0, 2);
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkImplicitClassMember(@NotNull PsiMember member, @NotNull LanguageLevel languageLevel,
                                                        @NotNull PsiFile psiFile) {
    if (!(member.getContainingClass() instanceof PsiImplicitClass implicitClass)) {
      return null;
    }

    PsiElement anchor = member;
    if (member instanceof PsiNameIdentifierOwner owner) {
      PsiElement nameIdentifier = owner.getNameIdentifier();
      if (nameIdentifier != null) {
        anchor = nameIdentifier;
      }
    }
    HighlightInfo.Builder builder = HighlightUtil.checkFeature(anchor, JavaFeature.IMPLICIT_CLASSES, languageLevel, psiFile);
    if (builder == null) return null;

    if (!(member instanceof PsiClass)) {
      boolean hasClassToRelocate = PsiTreeUtil.findChildOfType(implicitClass, PsiClass.class) != null;
      if (hasClassToRelocate) {
        MoveMembersIntoClassFix fix = new MoveMembersIntoClassFix(implicitClass);
        builder.registerFix(fix, null, null, null, null);
      }
    }

    return builder;
  }
}
