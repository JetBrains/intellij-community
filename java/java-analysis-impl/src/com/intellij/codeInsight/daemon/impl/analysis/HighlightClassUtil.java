// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.jvm.JvmModifier;
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
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
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
import java.util.stream.Collectors;

// generates HighlightInfoType.ERROR-only HighlightInfos at PsiClass level
public final class HighlightClassUtil {
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

    PsiClass superClass = abstractMethod.getContainingClass();
    if (superClass == null) {
      return null;
    }

    String messageKey;
    String referenceName;
    if (aClass instanceof PsiEnumConstantInitializer) {
      messageKey = "enum.constant.must.implement.method";

      PsiEnumConstantInitializer enumConstant = (PsiEnumConstantInitializer)aClass;
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

    String message = JavaErrorBundle.message(messageKey,
                                             referenceName,
                                             JavaHighlightUtil.formatMethod(abstractMethod),
                                             HighlightUtil.formatClass(superClass, false));

    HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message).create();
    PsiMethod anyMethodToImplement = ClassUtil.getAnyMethodToImplement(aClass);
    if (anyMethodToImplement != null) {
      if (!anyMethodToImplement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) ||
          JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, superClass)) {
        QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createImplementMethodsFix(implementsFixElement));
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
      PsiMethod anyAbstractMethod = ClassUtil.getAnyAbstractMethod(aClass);
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
    HighlightInfo info = createInfoAndRegisterRenameFix(aClass, dupFileName, "duplicate.class.in.other.file");
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createNavigateToDuplicateElementFix(dupClass));
    return info;
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
    PsiElement element = null;
    while (parent != null) {
      if (parent instanceof PsiFile) break;
      element = checkSiblings ? parent.getPrevSibling() : null;
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
      HighlightInfo info = createInfoAndRegisterRenameFix(aClass, name, "duplicate.class");
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createNavigateToDuplicateElementFix((PsiClass)element));
      return info;
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
    if (JavaHighlightUtil.isJavaHashBangScript(file)) return null;
    String message = JavaErrorBundle.message("public.class.should.be.named.after.file", aClass.getName());
    TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    HighlightInfo errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).
      range(aClass, range.getStartOffset(), range.getEndOffset()).
      descriptionAndTooltip(message).create();
    PsiClass[] classes = file.getClasses();
    boolean containsClassForFile = ContainerUtil.exists(classes, otherClass ->
                                                              !otherClass.getManager().areElementsEquivalent(otherClass, aClass) &&
                                                              otherClass.hasModifierProperty(PsiModifier.PUBLIC) &&
                                                              virtualFile.getNameWithoutExtension().equals(otherClass.getName()));
    if (!containsClassForFile) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createRenameFileFix(aClass.getName() + JavaFileType.DOT_DEFAULT_EXTENSION));
    }
    if (classes.length > 1) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createMoveClassToSeparateFileFix(aClass));
    }
    QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.PUBLIC, false, false));
    if (!containsClassForFile) {
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createRenameElementFix(aClass));
    }
    return errorResult;
  }

  static HighlightInfo checkClassMemberDeclaredOutside(@NotNull PsiErrorElement errorElement) {
    PsiJavaFile file = ObjectUtils.tryCast(errorElement.getContainingFile(), PsiJavaFile.class);
    if (file == null) return null;
    String fileName = FileUtilRt.getNameWithoutExtension(file.getName());
    if (!StringUtil.isJavaIdentifier(fileName)) return null;
    MemberModel model = MemberModel.create(errorElement);
    if (model == null) return null;
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(model.textRange())
      .description(JavaErrorBundle.message("class.member.declared.outside"))
      .create();
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createMoveMemberIntoClassFix(errorElement));
    return info;
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
  public static boolean isRestrictedIdentifier(@Nullable String typeName, @NotNull LanguageLevel level) {
    return PsiKeyword.VAR.equals(typeName) && HighlightingFeature.LVTI.isSufficient(level) ||
           PsiKeyword.YIELD.equals(typeName) && HighlightingFeature.SWITCH_EXPRESSION.isSufficient(level) ||
           PsiKeyword.RECORD.equals(typeName) && HighlightingFeature.RECORDS.isSufficient(level) ||
           (PsiKeyword.SEALED.equals(typeName) || PsiKeyword.PERMITS.equals(typeName)) && HighlightingFeature.SEALED_CLASSES.isSufficient(level);
  }

  static HighlightInfo checkClassAndPackageConflict(@NotNull PsiClass aClass) {
    String name = aClass.getQualifiedName();
    if (name == null) return null;
    if (CommonClassNames.DEFAULT_PACKAGE.equals(name)) {
      return createInfoAndRegisterRenameFix(aClass, name, "class.clashes.with.package");
    }

    PsiElement file = aClass.getParent();
    if (file instanceof PsiJavaFile && !((PsiJavaFile)file).getPackageName().isEmpty()) {
      PsiElement directory = file.getParent();
      if (directory instanceof PsiDirectory) {
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
  private static HighlightInfo createInfoAndRegisterRenameFix(@NotNull PsiClass aClass,
                                                              @NotNull String name,
                                                              @NotNull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String key) {
    String message = JavaErrorBundle.message(key, name);
    PsiIdentifier identifier = aClass.getNameIdentifier();
    if (identifier == null) return null;
    TextRange textRange = identifier.getTextRange();
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(message).create();
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createRenameFix(aClass, info));
    return info;
  }

  private static HighlightInfo checkStaticFieldDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiField.class) == null) {
      return null;
    }

    PsiField field = (PsiField)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(field) || PsiUtil.isCompileTimeConstant(field)) {
      return null;
    }

    HighlightInfo result = HighlightUtil.checkFeature(keyword, HighlightingFeature.INNER_STATICS,
                                                      PsiUtil.getLanguageLevel(field), field.getContainingFile());

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
    HighlightInfo result = HighlightUtil.checkFeature(keyword, HighlightingFeature.INNER_STATICS,
                                                      PsiUtil.getLanguageLevel(method), method.getContainingFile());
    QuickFixAction.registerQuickFixAction(result, QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.STATIC, false, false));
    registerMakeInnerClassStatic((PsiClass)method.getParent(), result);
    return result;
  }

  private static HighlightInfo checkStaticInitializerDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    if (getEnclosingStaticClass(keyword, PsiClassInitializer.class) == null) {
      return null;
    }
    PsiClassInitializer initializer = (PsiClassInitializer)keyword.getParent().getParent();
    if (PsiUtilCore.hasErrorElementChild(initializer)) return null;
    HighlightInfo result = HighlightUtil.checkFeature(keyword, HighlightingFeature.INNER_STATICS,
                                                      PsiUtil.getLanguageLevel(initializer), initializer.getContainingFile());
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
    HighlightInfo info = HighlightUtil.checkFeature(range, HighlightingFeature.INNER_STATICS,
                                                    PsiUtil.getLanguageLevel(aClass), aClass.getContainingFile());
    if (context != keyword) {
      QuickFixAction.registerQuickFixActions(info, null, JvmElementActionFactories
        .createModifierActions(aClass, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, false)));
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
          PsiClassType[] referencedTypes = list.getReferencedTypes();
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
    if (extendFrom != null && extendFrom.isInterface() != mustBeInterface) {
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
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(range)
        .descriptionAndTooltip(JavaErrorBundle.message("ambiguous.method.call", m1, m2))
        .create();

      QuickFixAction.registerQuickFixAction(info,QUICK_FIX_FACTORY.createCreateConstructorMatchingSuperFix(aClass));
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddDefaultConstructorFix(baseClass));
      return info;
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
      feature = aClass.isAnnotationType() ? null : HighlightingFeature.LOCAL_INTERFACES;
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
        .create();
    }
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
      String name = HighlightUtil.formatClass(aClass);
      String description = JavaErrorBundle.message("duplicate.class", name);
      HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createUnimplementInterfaceAction(name, true));
      return info;
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
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createDeleteFix(element));
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createConvertInterfaceToClassFix(aClass));
    return info;
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
          PsiClass baseClass = PsiUtil.resolveClassInType(((PsiAnonymousClass)aClass).getBaseClassType());
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
  static HighlightInfo checkClassExtendsForeignInnerClass(@NotNull PsiJavaCodeReferenceElement extendRef, @Nullable PsiElement resolved) {
    PsiElement parent = extendRef.getParent();
    if (!(parent instanceof PsiReferenceList)) {
      return null;
    }
    PsiElement grand = parent.getParent();
    if (!(grand instanceof PsiClass)) {
      return null;
    }
    PsiClass aClass = (PsiClass)grand;
    PsiClass containerClass;
    if (aClass instanceof PsiTypeParameter) {
      PsiTypeParameterListOwner owner = ((PsiTypeParameter)aClass).getOwner();
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
    HighlightInfo[] infos = new HighlightInfo[1];
    extendRef.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (infos[0] != null) return;
        super.visitElement(element);
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiClass) {
          PsiClass base = (PsiClass)resolve;
          PsiClass baseClass = base.getContainingClass();
          if (baseClass != null && base.hasModifierProperty(PsiModifier.PRIVATE) && baseClass == containerClass && baseClass.getContainingClass() == null) {
            String description = JavaErrorBundle.message("private.symbol",
                                                         HighlightUtil.formatClass(base),
                                                         HighlightUtil.formatClass(baseClass));
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
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

  static HighlightInfo checkCreateInnerClassFromStaticContext(@NotNull PsiNewExpression expression, @NotNull PsiType type, @NotNull PsiClass aClass) {
    if (type instanceof PsiArrayType || type instanceof PsiPrimitiveType) return null;
    if (aClass instanceof PsiAnonymousClass) {
      aClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
      if (aClass == null) return null;
    }

    PsiExpression qualifier = expression.getQualifier();
    return checkCreateInnerClassFromStaticContext(expression, qualifier, aClass);
  }

  public static HighlightInfo checkCreateInnerClassFromStaticContext(@NotNull PsiElement element,
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

  static HighlightInfo checkCreateInnerClassFromStaticContext(@NotNull PsiElement element,
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

  static HighlightInfo checkSuperQualifierType(@NotNull Project project, @NotNull PsiMethodCallExpression superCall) {
    if (!JavaPsiConstructorUtil.isSuperConstructorCall(superCall)) return null;
    PsiMethod ctr = PsiTreeUtil.getParentOfType(superCall, PsiMethod.class, true, PsiMember.class);
    if (ctr == null) return null;
    PsiClass aClass = ctr.getContainingClass();
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

  static HighlightInfo checkIllegalEnclosingUsage(@NotNull PsiElement place,
                                                  @Nullable PsiClass aClass,
                                                  @NotNull PsiClass outerClass,
                                                  @NotNull PsiElement elementToHighlight) {
    if (!PsiTreeUtil.isContextAncestor(outerClass, place, false)) {
      String description = JavaErrorBundle.message("is.not.an.enclosing.class", HighlightUtil.formatClass(outerClass));
      HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description).create();
      registerMakeInnerClassStatic(aClass, highlightInfo);
      return highlightInfo;
    }
    PsiModifierListOwner staticParent = PsiUtil.getEnclosingStaticElement(place, outerClass);
    if (staticParent != null) {
      String element = HighlightUtil.formatClass(outerClass) + "." +
                             (place instanceof PsiSuperExpression ? PsiKeyword.SUPER : PsiKeyword.THIS);
      String description = JavaErrorBundle.message("cannot.be.referenced.from.static.context", element);
      HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(description).create();
      // make context not static or referenced class static
      QuickFixAction.registerQuickFixAction(
        highlightInfo,
        QUICK_FIX_FACTORY.createModifierListFix(staticParent, PsiModifier.STATIC, false, false)
      );
      PsiModifierList classModifierList;
      if (aClass != null
          && (classModifierList = aClass.getModifierList()) != null
          && HighlightUtil.getIncompatibleModifier(PsiModifier.STATIC, classModifierList) == null) {
        QuickFixAction.registerQuickFixAction(
          highlightInfo,
          QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.STATIC, true, false)
        );
      }
      return highlightInfo;
    }
    return null;
  }

  static HighlightInfo checkWellFormedRecord(@NotNull PsiClass psiClass) {
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

  static HighlightInfo checkIllegalInstanceMemberInRecord(@NotNull PsiMember member) {
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

  static HighlightInfo checkExtendsProhibitedClass(@NotNull PsiClass superClass, @NotNull PsiClass psiClass, @NotNull PsiElement elementToHighlight) {
    String qualifiedName = superClass.getQualifiedName();
    if (CommonClassNames.JAVA_LANG_ENUM.equals(qualifiedName) && !psiClass.isEnum() || CommonClassNames.JAVA_LANG_RECORD.equals(qualifiedName) && !psiClass.isRecord()) {
      String message = JavaErrorBundle.message("classes.extends.prohibited.super", qualifiedName);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(message).create();
    }
    return null;
  }

  static HighlightInfo checkAnonymousInheritProhibited(@NotNull PsiNewExpression expression) {
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
  
  static HighlightInfo checkExtendsSealedClass(@NotNull PsiFunctionalExpression expression, @NotNull PsiType functionalInterfaceType) {
    PsiClass functionalInterface = PsiUtil.resolveClassInClassTypeOnly(functionalInterfaceType);
    if (functionalInterface == null || !functionalInterface.hasModifierProperty(PsiModifier.SEALED)) return null;
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(expression)
      .descriptionAndTooltip(JavaErrorBundle.message("sealed.cannot.be.functional.interface"))
      .create();
  }

   public static HighlightInfo checkExtendsSealedClass(@NotNull PsiClass aClass,
                                                       @NotNull PsiClass superClass,
                                                       @NotNull PsiJavaCodeReferenceElement elementToHighlight) {
    if (superClass.hasModifierProperty(PsiModifier.SEALED)) {
      if (PsiUtil.isLocalClass(aClass)) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(elementToHighlight)
          .descriptionAndTooltip(JavaErrorBundle.message("local.classes.must.not.extend.sealed.classes")).create();
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
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .descriptionAndTooltip(JavaErrorBundle.message("not.allowed.in.sealed.hierarchy", aClass.getName()))
        .range(elementToHighlight).create();
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddToPermitsListFix(aClass, superClass));
      return info;
    }
    return null;
  }

  static HighlightInfo checkAnonymousSealedProhibited(@NotNull PsiNewExpression newExpression) {
    PsiAnonymousClass aClass = newExpression.getAnonymousClass();
    if (aClass != null) {
      PsiClass superClass = aClass.getBaseClassType().resolve();
      if (superClass != null && superClass.hasModifierProperty(PsiModifier.SEALED)) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(aClass.getBaseClassReference())
          .descriptionAndTooltip(JavaErrorBundle.message("anonymous.classes.must.not.extend.sealed.classes")).create();
      }
    }
    return null;
  }

  static void checkPermitsList(@NotNull PsiReferenceList list, @NotNull HighlightInfoHolder holder) {
    PsiElement parent = list.getParent();
    if (parent instanceof PsiClass && list.equals(((PsiClass)parent).getPermitsList())) {
      PsiClass aClass = (PsiClass)parent;
      PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
      if (nameIdentifier == null) return;
      if (aClass.isEnum() || aClass.isRecord() || aClass.isAnnotationType()) {
        String description = aClass.isEnum() ? JavaErrorBundle.message("permits.after.enum") : null;
        if (description == null) {
          description = JavaErrorBundle.message(aClass.isRecord() ? "record.permits" : "annotation.type.permits");
        }
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(list)
          .descriptionAndTooltip(description)
          .create();
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createDeleteFix(list));
        holder.add(info);
        return;
      }
      if (!aClass.hasModifierProperty(PsiModifier.SEALED)) {
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(list.getFirstChild())
          .descriptionAndTooltip(JavaErrorBundle.message("invalid.permits.clause", aClass.getName()))
          .create();
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.SEALED, true, false));
        holder.add(info);
      }

      PsiJavaModule currentModule = JavaModuleGraphUtil.findDescriptorByElement(aClass);
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(aClass.getProject());
      for (PsiJavaCodeReferenceElement permitted : list.getReferenceElements()) {
        PsiReferenceParameterList parameterList = permitted.getParameterList();
        if (parameterList != null && parameterList.getTypeParameterElements().length > 0) {
          HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameterList)
            .descriptionAndTooltip(JavaErrorBundle.message("permits.list.generics.are.not.allowed")).create();
          holder.add(info);
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createDeleteFix(parameterList));
          continue;
        }
        @Nullable PsiElement resolve = permitted.resolve();
        if (resolve instanceof PsiClass) {
          PsiClass inheritorClass = (PsiClass)resolve;
          PsiManager manager = inheritorClass.getManager();
          if (!ContainerUtil.exists(inheritorClass.getSuperTypes(), type -> manager.areElementsEquivalent(aClass, type.resolve()))) {
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(permitted)
              .descriptionAndTooltip(JavaErrorBundle.message("invalid.permits.clause.direct.implementation",
                                                             inheritorClass.getName(),
                                                             inheritorClass.isInterface() == aClass.isInterface() ? 1 : 2,
                                                             aClass.getName()))
              .create();
            QuickFixAction.registerQuickFixActions(info, null,
                                                   QUICK_FIX_FACTORY.createExtendSealedClassFixes(permitted, aClass, inheritorClass));
            holder.add(info);
          }
          else {
            if (currentModule == null && !psiFacade.arePackagesTheSame(aClass, inheritorClass)) {
              HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(permitted)
                .descriptionAndTooltip(JavaErrorBundle.message("class.not.allowed.to.extend.sealed.class.from.another.package"))
                .create();
              PsiFile parentFile = aClass.getContainingFile();
              if (parentFile instanceof PsiClassOwner) {
                String parentPackage = ((PsiClassOwner)parentFile).getPackageName();
                QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createMoveClassToPackageFix(inheritorClass, parentPackage));
              }
              holder.add(info);
            }
            else if (currentModule != null && !areModulesTheSame(currentModule, JavaModuleGraphUtil.findDescriptorByElement(inheritorClass))) {
              holder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                           .range(permitted)
                           .descriptionAndTooltip(JavaErrorBundle.message("class.not.allowed.to.extend.sealed.class.from.another.module"))
                           .create());
            }
            else if (!hasPermittedSubclassModifier(inheritorClass)) {
              HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(permitted)
                .descriptionAndTooltip(JavaErrorBundle.message("permitted.subclass.must.have.modifier"))
                .create();
              IntentionAction markNonSealed = QUICK_FIX_FACTORY.createModifierListFix(inheritorClass, PsiModifier.NON_SEALED, true, false);
              QuickFixAction.registerQuickFixAction(info, markNonSealed);
              boolean hasInheritors = DirectClassInheritorsSearch.search(inheritorClass).findFirst() != null;
              IntentionAction action = hasInheritors ?
                                       QUICK_FIX_FACTORY.createSealClassFromPermitsListFix(inheritorClass) :
                                       QUICK_FIX_FACTORY.createModifierListFix(inheritorClass, PsiModifier.FINAL, true, false);
              QuickFixAction.registerQuickFixAction(info, action);
              holder.add(info);
            }
          }
        }
      }
    }
  }

  private static boolean areModulesTheSame(@NotNull PsiJavaModule module, PsiJavaModule module1) {
    return module1 != null && module.getOriginalElement() == module1.getOriginalElement();
  }

  static HighlightInfo checkSealedClassInheritors(@NotNull PsiClass psiClass) {
    if (psiClass.hasModifierProperty(PsiModifier.SEALED)) {
      PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
      if (nameIdentifier == null) return null;
      if (psiClass.isEnum()) return null;

      Collection<PsiClass> inheritors = DirectClassInheritorsSearch.search(psiClass).findAll();
      if (inheritors.isEmpty()) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(nameIdentifier)
          .descriptionAndTooltip(JavaErrorBundle.message("sealed.must.have.inheritors"))
          .create();
      }
      PsiFile parentFile = psiClass.getContainingFile();
      boolean hasOutsideClasses = inheritors.stream().anyMatch(inheritor -> inheritor.getContainingFile() != parentFile);
      if (hasOutsideClasses) {
        Map<PsiJavaCodeReferenceElement, PsiClass> permittedClassesRefs = getPermittedClassesRefs(psiClass);
        Collection<PsiClass> permittedClasses = permittedClassesRefs.values();
        boolean hasMissingInheritors = inheritors.stream().anyMatch(inheritor -> !permittedClasses.contains(inheritor));
        if (hasMissingInheritors) {
          HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(nameIdentifier)
            .descriptionAndTooltip(JavaErrorBundle.message("permit.list.must.contain.outside.inheritors"))
            .create();
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createFillPermitsListFix(nameIdentifier));
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

  static HighlightInfo checkSealedSuper(@NotNull PsiClass aClass) {
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
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(nameIdentifier)
        .descriptionAndTooltip(JavaErrorBundle.message("sealed.type.inheritor.expected.modifiers", PsiModifier.SEALED, PsiModifier.NON_SEALED, PsiModifier.FINAL)).create();
      if (canBeFinal) {
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.FINAL, true, false));
      }
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.SEALED, true, false));
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createModifierListFix(aClass, PsiModifier.NON_SEALED, true, false));
      return info;
    }
    return null;
  }

  static HighlightInfo checkShebangComment(@NotNull PsiComment comment) {
    if (comment.getTextOffset() != 0) {
      return null;
    }
    if (comment.getText().startsWith("#!")) {
      VirtualFile file = PsiUtilCore.getVirtualFile(comment);
      if (file != null && "java".equals(file.getExtension())) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .descriptionAndTooltip(JavaAnalysisBundle.message("text.shebang.mechanism.in.java.files.not.permitted"))
          .range(comment, 0, 2).create();
      }
    }
    return null;
  }
}
