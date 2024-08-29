// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageFeatureProvider;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo.ApplicabilityLevel;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.intellij.psi.PsiKeyword.*;

public final class PsiUtil extends PsiUtilCore {
  private static final Logger LOG = Logger.getInstance(PsiUtil.class);

  public static final int ACCESS_LEVEL_PUBLIC = 4;
  public static final int ACCESS_LEVEL_PROTECTED = 3;
  public static final int ACCESS_LEVEL_PACKAGE_LOCAL = 2;
  public static final int ACCESS_LEVEL_PRIVATE = 1;
  public static final Key<Boolean> VALID_VOID_TYPE_IN_CODE_FRAGMENT = Key.create("VALID_VOID_TYPE_IN_CODE_FRAGMENT");

  private static final Pattern IGNORED_NAMES = Pattern.compile("ignored?[A-Za-z\\d]*");

  private static final @NotNull Map<CharSequence, JavaFeature> SOFT_KEYWORDS = CollectionFactory.createCharSequenceMap(true);

  static {
    SOFT_KEYWORDS.put(VAR, JavaFeature.LVTI);
    SOFT_KEYWORDS.put(RECORD, JavaFeature.RECORDS);
    SOFT_KEYWORDS.put(YIELD, JavaFeature.SWITCH_EXPRESSION);
    SOFT_KEYWORDS.put(SEALED, JavaFeature.SEALED_CLASSES);
    SOFT_KEYWORDS.put(PERMITS, JavaFeature.SEALED_CLASSES);
    SOFT_KEYWORDS.put(WHEN, JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS);
    SOFT_KEYWORDS.put(OPEN, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(MODULE, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(REQUIRES, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(EXPORTS, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(OPENS, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(USES, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(PROVIDES, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(TRANSITIVE, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(TO, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(WITH, JavaFeature.MODULES);
  }

  private PsiUtil() {}

  public static boolean isOnAssignmentLeftHand(@NotNull PsiExpression expr) {
    PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class);
    return parent instanceof PsiAssignmentExpression &&
           PsiTreeUtil.isAncestor(((PsiAssignmentExpression)parent).getLExpression(), expr, false);
  }

  public static boolean isAccessibleFromPackage(@NotNull PsiModifierListOwner element, @NotNull PsiPackage aPackage) {
    if (element.hasModifierProperty(PsiModifier.PUBLIC)) return true;
    return !element.hasModifierProperty(PsiModifier.PRIVATE) &&
           JavaPsiFacade.getInstance(element.getProject()).isInPackage(element, aPackage);
  }

  public static boolean isAccessedForWriting(@NotNull PsiExpression expr) {
    if (isOnAssignmentLeftHand(expr)) return true;
    PsiElement parent = skipParenthesizedExprUp(expr.getParent());
    return isIncrementDecrementOperation(parent);
  }

  public static boolean isAccessedForReading(@NotNull PsiExpression expr) {
    PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class);
    return !(parent instanceof PsiAssignmentExpression) ||
           !PsiTreeUtil.isAncestor(((PsiAssignmentExpression)parent).getLExpression(), expr, false) ||
           ((PsiAssignmentExpression)parent).getOperationTokenType() != JavaTokenType.EQ;
  }

  public static boolean isAccessible(@NotNull PsiMember member, @NotNull PsiElement place, @Nullable PsiClass accessObjectClass) {
    return isAccessible(place.getProject(), member, place, accessObjectClass);
  }

  /**
   * Checks that a *resolved* to {@code member} reference at {@code place} is accessible: it's visible by visibility rules as well as
   * by module (java) rules.
   *
   * <p>NOTE:</p>
   * If there is no module (IDEA's) dependency from module with {@code place} on a module with {@code member},
   * then reference won't be resolved and this method will return {@code true}.
   * <p>
   * Please use {@link #isMemberAccessibleAt(PsiMember, PsiElement)} to catch these cases as well
   */
  public static boolean isAccessible(@NotNull Project project, @NotNull PsiMember member,
                                     @NotNull PsiElement place, @Nullable PsiClass accessObjectClass) {
    return JavaPsiFacade.getInstance(project).getResolveHelper().isAccessible(member, place, accessObjectClass);
  }

  /**
   * Checks that reference on {@code member} inserted at {@code place} will be resolved and accessible
   */
  public static boolean isMemberAccessibleAt(@NotNull PsiMember member, @NotNull PsiElement place) {
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(member);
    return (virtualFile == null || place.getResolveScope().contains(virtualFile)) && isAccessible(member, place, null);
  }

  @NotNull
  public static JavaResolveResult getAccessObjectClass(@NotNull PsiExpression expression) {
    if (expression instanceof PsiSuperExpression) return JavaResolveResult.EMPTY;
    PsiType type = expression.getType();
    JavaResolveResult accessObject = getAccessObjectClass(type, expression);
    if (accessObject != null) return accessObject;

    if (type == null && expression instanceof PsiReferenceExpression) {
      JavaResolveResult resolveResult = ((PsiReferenceExpression)expression).advancedResolve(false);
      if (resolveResult.getElement() instanceof PsiClass) {
        return resolveResult;
      }
    }
    return JavaResolveResult.EMPTY;
  }

  @Nullable
  private static JavaResolveResult getAccessObjectClass(@Nullable PsiType type, @NotNull PsiElement place) {
    if (type instanceof PsiClassType) {
      return ((PsiClassType)type).resolveGenerics();
    }
    if (type instanceof PsiDisjunctionType) {
      PsiType lub = ((PsiDisjunctionType)type).getLeastUpperBound();
      if (lub instanceof PsiClassType) {
        return ((PsiClassType)lub).resolveGenerics();
      }
    }

    if (type instanceof PsiCapturedWildcardType) {
      PsiType upperBound = ((PsiCapturedWildcardType)type).getUpperBound();
      if (upperBound instanceof PsiClassType) {
        PsiClass resolved = ((PsiClassType)upperBound).resolve();
        PsiFile containingFile = resolved != null ? resolved.getContainingFile() : null;
        String packageName = containingFile instanceof PsiClassOwner ? ((PsiClassOwner)containingFile).getPackageName() : null;
        String classText = StringUtil.isEmptyOrSpaces(packageName) ? "" : "package " + packageName + ";\n ";
        classText += "class I<T extends " + upperBound.getCanonicalText() + "> {}";
        PsiJavaFile file =
          (PsiJavaFile)PsiFileFactory.getInstance(place.getProject()).createFileFromText("inference_dummy.java", JavaLanguage.INSTANCE, classText);
        PsiTypeParameter freshParameter = file.getClasses()[0].getTypeParameters()[0];
        return new ClassCandidateInfo(freshParameter, PsiSubstitutor.EMPTY);
      }
    }

    if (type instanceof PsiArrayType) {
      return getAccessObjectClass(((PsiArrayType)type).getComponentType(), place);
    }
    return null;
  }

  public static boolean isConstantExpression(@Nullable PsiExpression expression) {
    return expression != null && JavaPsiFacade.getInstance(expression.getProject()).isConstantExpression(expression);
  }

  // todo: move to PsiThrowsList?
  public static void addException(@NotNull PsiMethod method, @NotNull String exceptionFQName) throws IncorrectOperationException {
    PsiClass exceptionClass = JavaPsiFacade.getInstance(method.getProject()).findClass(exceptionFQName, method.getResolveScope());
    addException(method, exceptionClass, exceptionFQName);
  }

  public static void addException(@NotNull PsiMethod method, @NotNull PsiClass exceptionClass) throws IncorrectOperationException {
    addException(method, exceptionClass, exceptionClass instanceof PsiTypeParameter ? exceptionClass.getName() : exceptionClass.getQualifiedName());
  }

  private static void addException(@NotNull PsiMethod method,
                                   @Nullable PsiClass exceptionClass,
                                   @Nullable String exceptionName) throws IncorrectOperationException {
    if (exceptionClass == null && exceptionName == null) {
      throw new IllegalArgumentException("One of exceptionName, exceptionClass must be not null");
    }
    PsiReferenceList throwsList = method.getThrowsList();
    PsiJavaCodeReferenceElement[] refs = throwsList.getReferenceElements();
    boolean replaced = false;
    for (PsiJavaCodeReferenceElement ref : refs) {
      if (exceptionClass != null && ref.isReferenceTo(exceptionClass)) return;
      PsiClass aClass = (PsiClass)ref.resolve();
      if (exceptionClass == null || aClass == null) {
        continue;
      }
      if (aClass.isInheritor(exceptionClass, true)) {
        if (replaced) {
          ref.delete();
        }
        else {
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
          PsiJavaCodeReferenceElement ref1;
          if (exceptionName != null) {
            ref1 = factory.createReferenceElementByFQClassName(exceptionName, method.getResolveScope());
          }
          else {
            PsiClassType type = factory.createType(exceptionClass);
            ref1 = factory.createReferenceElementByType(type);
          }
          ref.replace(ref1);
          replaced = true;
        }
      }
      else if (exceptionClass.isInheritor(aClass, true)) {
        return;
      }
    }
    if (replaced) return;

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    PsiJavaCodeReferenceElement ref;
    if (exceptionName != null) {
      ref = factory.createReferenceElementByFQClassName(exceptionName, method.getResolveScope());
    }
    else {
      PsiClass superClass = exceptionClass.getSuperClass();
      while (superClass != null && isLocalOrAnonymousClass(superClass)) {
        superClass = superClass.getSuperClass();
      }
      PsiClassType type = factory.createType(superClass != null ? superClass : exceptionClass);
      ref = factory.createReferenceElementByType(type);
    }
    throwsList.add(ref);
  }

  // todo: move to PsiThrowsList?
  public static void removeException(@NotNull PsiMethod method, String exceptionClass) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement[] refs = method.getThrowsList().getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      if (ref.getCanonicalText().equals(exceptionClass)) {
        ref.delete();
      }
    }
  }

  public static boolean isVariableNameUnique(@NotNull String name, @NotNull PsiElement place) {
    PsiResolveHelper helper = JavaPsiFacade.getInstance(place.getProject()).getResolveHelper();
    return helper.resolveAccessibleReferencedVariable(name, place) == null;
  }

  /**
   * @return enclosing outermost (method or class initializer) body but not higher than scope
   */
  @Nullable
  public static PsiElement getTopLevelEnclosingCodeBlock(@Nullable PsiElement element, PsiElement scope) {
    PsiElement blockSoFar = null;
    while (element != null) {
      // variable can be defined in for loop initializer
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiExpression) || parent instanceof PsiLambdaExpression) {
        if (element instanceof PsiCodeBlock || element instanceof PsiForStatement || element instanceof PsiForeachStatement) {
          blockSoFar = element;
        }

        if (parent instanceof PsiMethod
            && parent.getParent() instanceof PsiClass
            && !isLocalOrAnonymousClass((PsiClass)parent.getParent()))
          break;
        if (parent instanceof PsiClassInitializer && !(parent.getParent() instanceof PsiAnonymousClass)) break;
        if (parent instanceof PsiField && ((PsiField) parent).getInitializer() == element) {
          blockSoFar = element;
        }
        if (parent instanceof PsiClassLevelDeclarationStatement) {
          parent = parent.getParent();
        }
        if (element instanceof PsiClass && !isLocalOrAnonymousClass((PsiClass)element)) {
          break;
        }
        if (element instanceof PsiFile && PsiUtilCore.getTemplateLanguageFile(element) != null) {
          return element;
        }
      }
      if (element == scope) break;
      element = parent;
    }
    return blockSoFar;
  }

  public static boolean isLocalOrAnonymousClass(@NotNull PsiClass psiClass) {
    return psiClass instanceof PsiAnonymousClass || isLocalClass(psiClass);
  }

  public static boolean isLocalClass(@NotNull PsiClass psiClass) {
    PsiElement parent = psiClass.getParent();
    if (parent instanceof PsiDeclarationStatement && parent.getParent() instanceof PsiCodeBlock) {
      return true;
    }

    if (parent instanceof PsiClass) {
      return isLocalOrAnonymousClass((PsiClass)parent);
    }
    return false;
  }

  public static boolean isAbstractClass(@NotNull PsiClass clazz) {
    PsiModifierList modifierList = clazz.getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  /**
   * @return topmost code block where variable makes sense
   */
  @Nullable
  @Contract(pure = true)
  public static PsiElement getVariableCodeBlock(@NotNull PsiVariable variable, @Nullable PsiElement context) {
    PsiElement codeBlock = null;
    if (variable instanceof PsiParameter) {
      PsiElement declarationScope = ((PsiParameter)variable).getDeclarationScope();
      if (variable instanceof PsiPatternVariable) {
        return declarationScope;
      }
      if (declarationScope instanceof PsiCatchSection) {
        codeBlock = ((PsiCatchSection)declarationScope).getCatchBlock();
      }
      else if (declarationScope instanceof PsiForeachStatement) {
        codeBlock = ((PsiForeachStatement)declarationScope).getBody();
      }
      else if (declarationScope instanceof PsiMethod) {
        codeBlock = ((PsiMethod)declarationScope).getBody();
      } else if (declarationScope instanceof PsiLambdaExpression) {
        codeBlock = ((PsiLambdaExpression)declarationScope).getBody();
      }
      else if (declarationScope instanceof PsiCodeBlock) {
        codeBlock = declarationScope;
      }
    }
    else if (variable instanceof PsiResourceVariable) {
      PsiElement resourceList = variable.getParent();
      return resourceList != null ? resourceList.getParent() : null;  // use try statement as topmost
    }
    else if (variable instanceof PsiLocalVariable && variable.getParent() instanceof PsiForStatement) {
      return variable.getParent();
    }
    else if (variable instanceof PsiField && context != null) {
      PsiClass aClass = ((PsiField) variable).getContainingClass();
      while (context != null && context.getParent() != aClass) {
        context = context.getParent();
        if (context instanceof PsiClassLevelDeclarationStatement) return null;
      }
      return context instanceof PsiMethod ?
             ((PsiMethod) context).getBody() :
             context instanceof PsiClassInitializer ? ((PsiClassInitializer) context).getBody() : null;
    }
    else {
      PsiElement scope = variable.getParent() == null ? null : variable.getParent().getParent();
      codeBlock = getTopLevelEnclosingCodeBlock(variable, scope);
      if (codeBlock != null && codeBlock.getParent() instanceof PsiSwitchStatement) codeBlock = codeBlock.getParent().getParent();
    }
    return codeBlock;
  }

  @Contract("null -> false")
  public static boolean isIncrementDecrementOperation(@Nullable PsiElement element) {
    if (element instanceof PsiUnaryExpression) {
      IElementType sign = ((PsiUnaryExpression)element).getOperationTokenType();
      return sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS;
    }
    return false;
  }

  @Unmodifiable
  public static @NotNull List<PsiExpression> getSwitchResultExpressions(@NotNull PsiSwitchExpression switchExpression) {
    PsiCodeBlock body = switchExpression.getBody();
    if (body != null) {
      List<PsiExpression> result = new ArrayList<>();
      PsiStatement[] statements = body.getStatements();
      for (PsiStatement statement : statements) {
        if (statement instanceof PsiSwitchLabeledRuleStatement) {
          PsiStatement ruleBody = ((PsiSwitchLabeledRuleStatement)statement).getBody();
          if (ruleBody instanceof PsiExpressionStatement) {
            result.add(((PsiExpressionStatement)ruleBody).getExpression());
          }
          else if (ruleBody instanceof PsiBlockStatement) {
            collectSwitchResultExpressions(result, ruleBody);
          }
        }
        else {
           collectSwitchResultExpressions(result, statement);
        }
      }
      return result;
    }
    return Collections.emptyList();
  }

  private static void collectSwitchResultExpressions(@NotNull List<? super PsiExpression> result, @NotNull PsiElement container) {
    List<PsiYieldStatement> yields = new ArrayList<>();
    addStatements(yields, container, PsiYieldStatement.class, element -> element instanceof PsiSwitchExpression);
    for (PsiYieldStatement statement : yields) {
      ContainerUtil.addIfNotNull(result, statement.getExpression());
    }
  }

  @MagicConstant(intValues = {ACCESS_LEVEL_PUBLIC, ACCESS_LEVEL_PROTECTED, ACCESS_LEVEL_PACKAGE_LOCAL, ACCESS_LEVEL_PRIVATE})
  public @interface AccessLevel {}

  @AccessLevel
  public static int getAccessLevel(@NotNull PsiModifierList modifierList) {
    if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
      return ACCESS_LEVEL_PRIVATE;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      return ACCESS_LEVEL_PACKAGE_LOCAL;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
      return ACCESS_LEVEL_PROTECTED;
    }
    return ACCESS_LEVEL_PUBLIC;
  }

  /**
   * @see com.intellij.lang.jvm.util.JvmUtil#getAccessModifier(int) for JVM language analogue.
   */
  @PsiModifier.ModifierConstant
  @NotNull
  public static String getAccessModifier(@AccessLevel int accessLevel) {
    if (accessLevel <= 0 || accessLevel > accessModifiers.length) {
      throw new IllegalArgumentException("Unknown level:" + accessLevel);
    }
    @SuppressWarnings("UnnecessaryLocalVariable") @PsiModifier.ModifierConstant String modifier =  accessModifiers[accessLevel - 1];
    return modifier;
  }

  private static final String[] accessModifiers = {
    PsiModifier.PRIVATE, PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PUBLIC
  };

  /**
   * @return true if element specified is statement or expression statement. see JLS 14.5-14.8
   */
  public static boolean isStatement(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();

    if (element instanceof PsiExpressionListStatement) {
      // statement list allowed in for() init or update only
      if (!(parent instanceof PsiForStatement)) return false;
      PsiForStatement forStatement = (PsiForStatement)parent;
      if (!(element == forStatement.getInitialization() || element == forStatement.getUpdate())) return false;
      PsiExpressionList expressionList = ((PsiExpressionListStatement)element).getExpressionList();
      for (PsiExpression expression : expressionList.getExpressions()) {
        if (!isStatement(expression)) return false;
      }
      return true;
    }

    if (element instanceof PsiExpressionStatement) {
      return parent instanceof PsiSwitchLabeledRuleStatement && ((PsiSwitchLabeledRuleStatement)parent).getEnclosingSwitchBlock() instanceof PsiSwitchExpression ||
             isStatement(((PsiExpressionStatement)element).getExpression());
    }

    if (element instanceof PsiDeclarationStatement) {
      if (parent instanceof PsiCodeBlock) return true;
      if (parent instanceof PsiCodeFragment) return true;
      if (!(parent instanceof PsiForStatement) || ((PsiForStatement)parent).getBody() == element) {
        return false;
      }
    }

    if (element instanceof PsiStatement) return true;
    if (element instanceof PsiAssignmentExpression) return true;
    if (isIncrementDecrementOperation(element)) return true;
    if (element instanceof PsiMethodCallExpression) return true;
    if (element instanceof PsiNewExpression) {
      return !(((PsiNewExpression)element).getType() instanceof PsiArrayType);
    }

    return element instanceof PsiCodeBlock;
  }

  @Nullable
  public static PsiElement getEnclosingStatement(PsiElement element) {
    while (element != null) {
      if (element.getParent() instanceof PsiCodeBlock) return element;
      element = element.getParent();
    }
    return null;
  }


  @Nullable
  public static PsiElement getElementInclusiveRange(@NotNull PsiElement scope, @NotNull TextRange range) {
    PsiElement psiElement = scope.findElementAt(range.getStartOffset());
    while (psiElement != null && !psiElement.getTextRange().contains(range)) {
      if (psiElement == scope) return null;
      psiElement = psiElement.getParent();
    }
    return psiElement;
  }

  @Nullable
  public static PsiClass resolveClassInType(@Nullable PsiType type) {
    if (type instanceof PsiClassType) {
      return ((PsiClassType) type).resolve();
    }
    if (type instanceof PsiArrayType) {
      return resolveClassInType(((PsiArrayType) type).getComponentType());
    }
    if (type instanceof PsiDisjunctionType) {
      PsiType lub = ((PsiDisjunctionType)type).getLeastUpperBound();
      if (lub instanceof PsiClassType) {
        return ((PsiClassType)lub).resolve();
      }
    }
    return null;
  }

  @Nullable
  public static PsiClass resolveClassInClassTypeOnly(@Nullable PsiType type) {
    return type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
  }

  @NotNull
  public static PsiClassType.ClassResolveResult resolveGenericsClassInType(@Nullable PsiType type) {
    if (type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType) type;
      return classType.resolveGenerics();
    }
    if (type instanceof PsiArrayType) {
      return resolveGenericsClassInType(((PsiArrayType) type).getComponentType());
    }
    if (type instanceof PsiDisjunctionType) {
      PsiType lub = ((PsiDisjunctionType)type).getLeastUpperBound();
      if (lub instanceof PsiClassType) {
        return ((PsiClassType)lub).resolveGenerics();
      }
    }
    return PsiClassType.ClassResolveResult.EMPTY;
  }

  @NotNull
  public static PsiType convertAnonymousToBaseType(@NotNull PsiType type) {
    PsiClass psiClass = resolveClassInType(type);
    if (psiClass instanceof PsiAnonymousClass) {
      type = PsiTypesUtil.createArrayType(((PsiAnonymousClass) psiClass).getBaseClassType(), type.getArrayDimensions());
    }
    return type;
  }

  public static boolean isApplicable(@NotNull PsiMethod method,
                                     @NotNull PsiSubstitutor substitutorForMethod,
                                     @NotNull PsiExpressionList argList) {
    return getApplicabilityLevel(method, substitutorForMethod, argList) != ApplicabilityLevel.NOT_APPLICABLE;
  }

  public static boolean isApplicable(@NotNull PsiMethod method,
                                     @NotNull PsiSubstitutor substitutorForMethod,
                                     PsiExpression @NotNull [] argList) {
    PsiType[] types = ContainerUtil.map2Array(argList, PsiType.class, PsiExpression.EXPRESSION_TO_TYPE);
    return getApplicabilityLevel(method, substitutorForMethod, types, getLanguageLevel(method)) != ApplicabilityLevel.NOT_APPLICABLE;
  }

  @MethodCandidateInfo.ApplicabilityLevelConstant
  public static int getApplicabilityLevel(@NotNull PsiMethod method,
                                          @NotNull PsiSubstitutor substitutorForMethod,
                                          @NotNull PsiExpressionList argList) {
    return getApplicabilityLevel(method, substitutorForMethod, argList.getExpressionTypes(), getLanguageLevel(argList));
  }

  @MethodCandidateInfo.ApplicabilityLevelConstant
  public static int getApplicabilityLevel(@NotNull PsiMethod method,
                                          @NotNull PsiSubstitutor substitutorForMethod,
                                          PsiType @NotNull [] args,
                                          @NotNull LanguageLevel languageLevel) {
    return getApplicabilityLevel(method, substitutorForMethod, args, languageLevel,
                                 true, true, ApplicabilityChecker.ASSIGNABILITY_CHECKER);
  }


  @FunctionalInterface
  public interface ApplicabilityChecker {
    ApplicabilityChecker ASSIGNABILITY_CHECKER =
      (left, right, allowUncheckedConversion, argId) -> TypeConversionUtil.isAssignable(left, right, allowUncheckedConversion);

    boolean isApplicable(PsiType left, PsiType right, boolean allowUncheckedConversion, int argId);
  }

  @MethodCandidateInfo.ApplicabilityLevelConstant
  public static int getApplicabilityLevel(@NotNull PsiMethod method,
                                          @NotNull PsiSubstitutor substitutorForMethod,
                                          PsiType @NotNull [] args,
                                          @NotNull LanguageLevel languageLevel,
                                          boolean allowUncheckedConversion,
                                          boolean checkVarargs,
                                          @NotNull ApplicabilityChecker function) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (args.length < parameters.length - 1) return ApplicabilityLevel.NOT_APPLICABLE;

    PsiClass containingClass = method.getContainingClass();
    boolean isRaw = containingClass != null && isRawSubstitutor(method, substitutorForMethod) && isRawSubstitutor(containingClass, substitutorForMethod);
    if (!areFirstArgumentsApplicable(args, parameters, languageLevel, substitutorForMethod, isRaw, allowUncheckedConversion, function)) {
      return ApplicabilityLevel.NOT_APPLICABLE;
    }
    if (args.length == parameters.length) {
      if (parameters.length == 0) return ApplicabilityLevel.FIXED_ARITY;
      PsiType parmType = getParameterType(parameters[parameters.length - 1], languageLevel, substitutorForMethod);
      PsiType argType = args[args.length - 1];
      if (argType == null) return ApplicabilityLevel.NOT_APPLICABLE;
      if (function.isApplicable(parmType, argType, allowUncheckedConversion, parameters.length - 1)) return ApplicabilityLevel.FIXED_ARITY;

      if (isRaw) {
        PsiType erasedParamType = TypeConversionUtil.erasure(parmType);
        if (erasedParamType != null && function.isApplicable(erasedParamType, argType, allowUncheckedConversion, parameters.length - 1)) {
          return ApplicabilityLevel.FIXED_ARITY;
        }
      }
    }

    if (checkVarargs && method.isVarArgs() && languageLevel.compareTo(LanguageLevel.JDK_1_5) >= 0) {
      if (args.length < parameters.length) return ApplicabilityLevel.VARARGS;
      PsiParameter lastParameter = parameters.length == 0 ? null : parameters[parameters.length - 1];
      if (lastParameter == null || !lastParameter.isVarArgs()) return ApplicabilityLevel.NOT_APPLICABLE;
      PsiType lastParmType = getParameterType(lastParameter, languageLevel, substitutorForMethod);
      if (!(lastParmType instanceof PsiArrayType)) return ApplicabilityLevel.NOT_APPLICABLE;
      lastParmType = ((PsiArrayType)lastParmType).getComponentType();
      if (lastParmType instanceof PsiCapturedWildcardType &&
          !JavaVersionService.getInstance().isAtLeast(((PsiCapturedWildcardType)lastParmType).getContext(), JavaSdkVersion.JDK_1_8)) {
        lastParmType = ((PsiCapturedWildcardType)lastParmType).getWildcard();
      }
      if (lastParmType instanceof PsiClassType) {
        lastParmType = ((PsiClassType)lastParmType).setLanguageLevel(languageLevel);
      }
      for (int i = parameters.length - 1; i < args.length; i++) {
        PsiType argType = args[i];
        if (argType == null || !function.isApplicable(lastParmType, argType, allowUncheckedConversion, i)) {
          return ApplicabilityLevel.NOT_APPLICABLE;
        }
      }
      return ApplicabilityLevel.VARARGS;
    }

    return ApplicabilityLevel.NOT_APPLICABLE;
  }

  private static boolean areFirstArgumentsApplicable(PsiType @NotNull [] args,
                                                     PsiParameter @NotNull [] parameters,
                                                     @NotNull LanguageLevel languageLevel,
                                                     @NotNull PsiSubstitutor substitutorForMethod,
                                                     boolean isRaw,
                                                     boolean allowUncheckedConversion, ApplicabilityChecker function) {
    for (int i = 0; i < parameters.length - 1; i++) {
      PsiType type = args[i];
      if (type == null) return false;
      PsiParameter parameter = parameters[i];
      PsiType substitutedParmType = getParameterType(parameter, languageLevel, substitutorForMethod);
      if (isRaw) {
        PsiType substErasure = TypeConversionUtil.erasure(substitutedParmType);
        if (substErasure != null && !function.isApplicable(substErasure, type, allowUncheckedConversion, i)) {
          return false;
        }
      }
      else if (!function.isApplicable(substitutedParmType, type, allowUncheckedConversion, i)) {
        return false;
      }
    }
    return true;
  }

  private static PsiType getParameterType(@NotNull PsiParameter parameter,
                                          @NotNull LanguageLevel languageLevel,
                                          @NotNull PsiSubstitutor substitutor) {
    PsiType parmType = parameter.getType();
    if (parmType instanceof PsiClassType) {
      parmType = ((PsiClassType)parmType).setLanguageLevel(languageLevel);
    }
    return substitutor.substitute(parmType);
  }

  /**
   * Compares types with respect to type parameter bounds: e.g. for
   * {@code class Foo<T extends Number>{}} types Foo&lt;?&gt; and Foo&lt;? extends Number&gt;
   * would be equivalent
   */
  public static boolean equalOnEquivalentClasses(@NotNull PsiClassType thisClassType,
                                                 @NotNull PsiClass aClass,
                                                 @NotNull PsiClassType otherClassType,
                                                 @NotNull PsiClass bClass) {
    PsiClassType capture1 = !PsiCapturedWildcardType.isCapture()
                                  ? thisClassType : (PsiClassType)captureToplevelWildcards(thisClassType, aClass);
    PsiClassType capture2 = !PsiCapturedWildcardType.isCapture()
                                  ? otherClassType : (PsiClassType)captureToplevelWildcards(otherClassType, bClass);

    PsiClassType.ClassResolveResult result1 = capture1.resolveGenerics();
    PsiClassType.ClassResolveResult result2 = capture2.resolveGenerics();

    return equalOnEquivalentClasses(result1.getSubstitutor(), aClass, result2.getSubstitutor(), bClass);
  }

  public static boolean equalOnEquivalentClasses(@NotNull PsiSubstitutor s1,
                                                 @NotNull PsiClass aClass,
                                                 @NotNull PsiSubstitutor s2,
                                                 @NotNull PsiClass bClass) {
    if (s1 == s2 && aClass == bClass) return true;
    // assume generic class equals to non-generic
    if (aClass.hasTypeParameters() != bClass.hasTypeParameters()) return true;
    PsiTypeParameter[] typeParameters1 = aClass.getTypeParameters();
    PsiTypeParameter[] typeParameters2 = bClass.getTypeParameters();
    if (typeParameters1.length != typeParameters2.length) return false;
    for (int i = 0; i < typeParameters1.length; i++) {
      PsiType substituted2 = s2.substitute(typeParameters2[i]);
      PsiType substituted1 = s1.substitute(typeParameters1[i]);
      if (!Comparing.equal(substituted1, substituted2)) return false;
    }
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) return true;
    PsiClass containingClass1 = aClass.getContainingClass();
    PsiClass containingClass2 = bClass.getContainingClass();

    if (containingClass1 != null && containingClass2 != null) {
      return equalOnEquivalentClasses(s1, containingClass1, s2, containingClass2);
    }

    if (containingClass1 == null && containingClass2 == null) {
      if (aClass == bClass && isLocalClass(aClass)) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
        return containingClass != null && equalOnEquivalentClasses(s1, containingClass, s2, containingClass);
      }
      return true;
    }

    return false;

  }

  /**
   * JLS 15.28
   */
  public static boolean isCompileTimeConstant(@NotNull PsiVariable field) {
    if (!field.hasModifierProperty(PsiModifier.FINAL)) return false;
    PsiType type = field.getType();
    return (TypeConversionUtil.isPrimitiveAndNotNull(type) || type.equalsToText(CommonClassNames.JAVA_LANG_STRING))
           && field.hasInitializer()
           && isConstantExpression(field.getInitializer());
  }

  public static boolean allMethodsHaveSameSignature(PsiMethod @NotNull [] methods) {
    if (methods.length == 0) return true;
    MethodSignature methodSignature = methods[0].getSignature(PsiSubstitutor.EMPTY);
    for (int i = 1; i < methods.length; i++) {
      PsiMethod method = methods[i];
      if (!methodSignature.equals(method.getSignature(PsiSubstitutor.EMPTY))) return false;
    }
    return true;
  }

  @Nullable
  @Contract("null -> null")
  public static PsiExpression deparenthesizeExpression(PsiExpression expression) {
    while (true) {
      if (expression instanceof PsiParenthesizedExpression) {
        expression = ((PsiParenthesizedExpression)expression).getExpression();
        continue;
      }
      if (expression instanceof PsiTypeCastExpression) {
        expression = ((PsiTypeCastExpression)expression).getOperand();
        continue;
      }
      return expression;
    }
  }

  /**
   * Checks whether given class is inner (as opposed to nested)
   *
   */
  public static boolean isInnerClass(@NotNull PsiClass aClass) {
    return !aClass.hasModifierProperty(PsiModifier.STATIC) && aClass.getContainingClass() != null;
  }

  @Nullable
  public static PsiElement findModifierInList(@NotNull PsiModifierList modifierList, String modifier) {
    PsiElement[] children = modifierList.getChildren();
    for (PsiElement child : children) {
      if (child.getText().equals(modifier)) return child;
    }
    return null;
  }

  @Nullable
  public static PsiClass getTopLevelClass(@NotNull PsiElement element) {
    PsiClass topClass = JBIterable.generate(element, PsiElement::getParent).takeWhile(e -> !(e instanceof PsiFile)).filter(PsiClass.class).last();
    return topClass instanceof PsiTypeParameter ? null : topClass;
  }

  @NlsSafe
  @Nullable
  public static String getPackageName(@NotNull PsiClass aClass) {
    PsiClass topClass = getTopLevelClass(aClass);
    if (topClass != null) {
      String fqName = topClass.getQualifiedName();
      if (fqName != null) {
        return StringUtil.getPackageName(fqName);
      }
    }

    PsiFile file = aClass.getContainingFile();
    if (file instanceof PsiClassOwner) {
      return ((PsiClassOwner)file).getPackageName();
    }

    return null;
  }

  /**
   * @param place place to start traversal
   * @param aClass level to stop traversal
   * @return element with static modifier enclosing place and enclosed by aClass (if not null).
   * Note that traversal goes through context elements.
   */
  @Nullable
  public static PsiModifierListOwner getEnclosingStaticElement(@NotNull PsiElement place, @Nullable PsiClass aClass) {
    LOG.assertTrue(aClass == null || !place.isPhysical() || PsiTreeUtil.isContextAncestor(aClass, place, false));
    PsiElement parent = place;
    while (parent != aClass) {
      if (parent == null) return null;
      if (parent instanceof PsiModifierListOwner && ((PsiModifierListOwner)parent).hasModifierProperty(PsiModifier.STATIC)) {
        return (PsiModifierListOwner)parent;
      }
      parent = parent.getContext();
    }
    return null;
  }

  /**
   * @param element element to get the associated type from (return type for method, or variable type for variable)
   * @return the associated type, might be null
   */
  @Nullable
  public static PsiType getTypeByPsiElement(@NotNull PsiElement element) {
    if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getType();
    }
    if (element instanceof PsiMethod) return ((PsiMethod)element).getReturnType();
    return null;
  }

  /**
   * Applies capture conversion to the type in context
   */
  @NotNull
  public static PsiType captureToplevelWildcards(@NotNull PsiType type, @NotNull PsiElement context) {
    if (!(type instanceof PsiClassType)) return type;
    PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
    PsiClass aClass = result.getElement();
    if (aClass == null) return type;
    PsiClassType updatedType = getSubstitutorWithWildcardsCaptured(context, result);
    return updatedType == null ? type : updatedType;
  }

  /**
   * Applies capture conversion to the {@link PsiClassType.ClassResolveResult}.
   */
  public static PsiClassType.@NotNull ClassResolveResult captureTopLevelWildcards(PsiClassType.@NotNull ClassResolveResult result) {
    PsiClass aClass = result.getElement();
    if (aClass == null) return result;
    PsiClassType updatedType = getSubstitutorWithWildcardsCaptured(aClass, result);
    return updatedType == null ? result : updatedType.resolveGenerics();
  }

  private static @Nullable PsiClassType getSubstitutorWithWildcardsCaptured(
    @NotNull PsiElement context, PsiClassType.@NotNull ClassResolveResult result) {
    PsiClass aClass = result.getElement();
    if (aClass == null) return null;
    PsiSubstitutor substitutor = result.getSubstitutor();

    PsiSubstitutor captureSubstitutor = substitutor;
    for (PsiTypeParameter typeParameter : typeParametersIterable(aClass)) {
      PsiType substituted = substitutor.substitute(typeParameter);
      if (substituted instanceof PsiWildcardType) {
        captureSubstitutor =
          captureSubstitutor.put(typeParameter, PsiCapturedWildcardType.create((PsiWildcardType)substituted, context, typeParameter));
      }
    }

    if (captureSubstitutor == substitutor) return null;
    Map<PsiTypeParameter, PsiType> substitutionMap = null;
    for (PsiTypeParameter typeParameter : typeParametersIterable(aClass)) {
      PsiType substituted = substitutor.substitute(typeParameter);
      if (substituted instanceof PsiWildcardType) {
        if (substitutionMap == null) substitutionMap = new HashMap<>(substitutor.getSubstitutionMap());
        PsiCapturedWildcardType capturedWildcard = (PsiCapturedWildcardType)captureSubstitutor.substitute(typeParameter);
        LOG.assertTrue(capturedWildcard != null);
        PsiType upperBound = PsiCapturedWildcardType.captureUpperBound(typeParameter, (PsiWildcardType)substituted, captureSubstitutor);
        if (upperBound != null) {
          capturedWildcard.setUpperBound(upperBound);
        }
        substitutionMap.put(typeParameter, capturedWildcard);
      }
    }

    if (substitutionMap == null) return null;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());
    return factory.createType(aClass, factory.createSubstitutor(substitutionMap));
  }

  public static boolean isInsideJavadocComment(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiDocComment.class, true, PsiMember.class) != null;
  }

  @NotNull
  @Unmodifiable
  public static List<PsiTypeElement> getParameterTypeElements(@NotNull PsiParameter parameter) {
    PsiTypeElement typeElement = parameter.getTypeElement();
    return typeElement != null && typeElement.getType() instanceof PsiDisjunctionType
           ? PsiTreeUtil.getChildrenOfTypeAsList(typeElement, PsiTypeElement.class)
           : Collections.singletonList(typeElement);
  }

  public static void checkIsIdentifier(@NotNull PsiManager manager, String text) throws IncorrectOperationException{
    if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(text)){
      throw new IncorrectOperationException(JavaPsiBundle.message("0.is.not.an.identifier", text) );
    }
  }

  @Nullable
  public static VirtualFile getJarFile(@NotNull PsiElement candidate) {
    VirtualFile file = candidate.getContainingFile().getVirtualFile();
    if (file != null && file.getFileSystem().getProtocol().equals("jar")) {
      return VfsUtilCore.getVirtualFileForJar(file);
    }
    return file;
  }

  public static boolean isAnnotationMethod(PsiElement element) {
    if (!(element instanceof PsiAnnotationMethod)) return false;
    PsiClass psiClass = ((PsiAnnotationMethod)element).getContainingClass();
    return psiClass != null && psiClass.isAnnotationType();
  }

  /**
   * Returns a suitable modifier for a newly generated member of the specified class.
   *
   * @param aClass  the class that will get a new member
   * @param constructor  specify true if the new member is a constructor, false otherwise.
   * @return a modifier based on the visibility and type of the specified class
   */
  @PsiModifier.ModifierConstant
  public static String getSuitableModifierForMember(@Nullable PsiClass aClass, boolean constructor) {
    String modifier = PsiModifier.PUBLIC;

    if (aClass != null && (!aClass.isRecord() || constructor) && !aClass.isInterface()) {
      if (aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) || constructor && aClass.isEnum()) {
        // enum constructors are implicitly private
        modifier = PsiModifier.PACKAGE_LOCAL;
      }
      else if (aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        modifier = PsiModifier.PRIVATE;
      }
      else if (aClass.hasModifierProperty(PsiModifier.PROTECTED) ||
          constructor && aClass.hasModifierProperty(PsiModifier.ABSTRACT) && aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        modifier = PsiModifier.PROTECTED;
      }
    }

    return modifier;
  }

  /*
   * Returns iterator of type parameters visible in owner. Type parameters are iterated in
   * inner-to-outer, right-to-left order.
   */
  @NotNull
  public static Iterator<PsiTypeParameter> typeParametersIterator(@NotNull PsiTypeParameterListOwner owner) {
    return typeParametersIterable(owner).iterator();
  }

  @NotNull
  public static Iterable<PsiTypeParameter> typeParametersIterable(@NotNull PsiTypeParameterListOwner owner) {
    Iterable<PsiTypeParameter> result = null;

    PsiTypeParameterListOwner currentOwner = owner;
    while (currentOwner != null) {
      PsiTypeParameter[] typeParameters = currentOwner.getTypeParameters();
      if (typeParameters.length > 0) {
        Iterable<PsiTypeParameter> iterable = () -> new Iterator<PsiTypeParameter>() {
          int idx = typeParameters.length - 1;

          @Override
          public boolean hasNext() {
            return idx >= 0;
          }

          @Override
          public PsiTypeParameter next() {
            if (idx < 0) throw new NoSuchElementException();
            return typeParameters[idx--];
          }
        };
        if (result == null) {
          result = iterable;
        } else {
          result = ContainerUtil.concat(result, iterable);
        }
      }

      if (currentOwner.hasModifierProperty(PsiModifier.STATIC)) break;
      if (currentOwner instanceof PsiClass && isLocalClass((PsiClass)currentOwner)) {
        currentOwner = PsiTreeUtil.getParentOfType(currentOwner, PsiTypeParameterListOwner.class);
        continue;
      }
      currentOwner = currentOwner.getContainingClass();
    }

    if (result == null) return Collections.emptyList();
    return result;
  }

  public static boolean canBeOverridden(@NotNull PsiMethod method) {
    PsiClass parentClass = method.getContainingClass();
    return parentClass != null &&
           !method.isConstructor() &&
           !method.hasModifierProperty(PsiModifier.STATIC) &&
           !method.hasModifierProperty(PsiModifier.FINAL) &&
           !method.hasModifierProperty(PsiModifier.PRIVATE) &&
           !(parentClass instanceof PsiAnonymousClass) &&
           !parentClass.hasModifierProperty(PsiModifier.FINAL);
  }

  public static PsiElement @NotNull [] mapElements(ResolveResult @NotNull [] candidates) {
    PsiElement[] result = new PsiElement[candidates.length];
    for (int i = 0; i < candidates.length; i++) {
      result[i] = candidates[i].getElement();
    }
    return result;
  }

  @Nullable
  public static PsiMember findEnclosingConstructorOrInitializer(PsiElement expression) {
    PsiMember parent = PsiTreeUtil.getParentOfType(expression, PsiClassInitializer.class, PsiEnumConstantInitializer.class, PsiMethod.class, PsiField.class);
    if (parent instanceof PsiMethod && !((PsiMethod)parent).isConstructor()) return null;
    return parent;
  }

  public static boolean checkName(@NotNull PsiElement element, @NotNull String name, @NotNull PsiElement context) {
    if (element instanceof PsiVariable && ((PsiVariable)element).isUnnamed()) return false;
    if (element instanceof PsiMetaOwner) {
      PsiMetaData data = ((PsiMetaOwner) element).getMetaData();
      if (data != null) {
        return name.equals(data.getName(context));
      }
    }
    return element instanceof PsiNamedElement && name.equals(((PsiNamedElement)element).getName());
  }

  public static boolean isRawSubstitutor(@NotNull PsiTypeParameterListOwner owner, @NotNull PsiSubstitutor substitutor) {
    if (!substitutor.hasRawSubstitution()) return false;

    for (PsiTypeParameter parameter : typeParametersIterable(owner)) {
      if (substitutor.substitute(parameter) == null) return true;
    }
    return false;
  }

  public static final Key<LanguageLevel> FILE_LANGUAGE_LEVEL_KEY = LanguageLevel.FILE_LANGUAGE_LEVEL_KEY;

  public static boolean isLanguageLevel5OrHigher(@NotNull PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_5);
  }

  /**
   * @deprecated inline or use {@code PsiUtil.isAvailable(JavaFeature.OVERRIDE_INTERFACE, element)}
   */
  @Deprecated
  public static boolean isLanguageLevel6OrHigher(@NotNull PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_6);
  }

  public static boolean isLanguageLevel7OrHigher(@NotNull PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_7);
  }

  public static boolean isLanguageLevel8OrHigher(@NotNull PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_8);
  }

  public static boolean isLanguageLevel9OrHigher(@NotNull PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_9);
  }

  /**
   * @param feature feature to check
   * @param element a valid PsiElement to check (it's better to supply PsiFile if already known; any element is accepted for convenience)
   * @return true if the feature is available in the PsiFile the supplied element belongs to
   */
  public static boolean isAvailable(@NotNull JavaFeature feature, @NotNull PsiElement element) {
    if (!feature.isSufficient(getLanguageLevel(element))) return false;
    if (!feature.canBeCustomized()) return true;
    PsiFile file = element.getContainingFile();
    if (file == null) return true;
    for (LanguageFeatureProvider extension : LanguageFeatureProvider.EXTENSION_POINT_NAME.getExtensionList()) {
      ThreeState threeState = extension.isFeatureSupported(feature, file);
      if (threeState != ThreeState.UNSURE)
        return threeState.toBoolean();
    }
    return true;
  }

  /**
   * Returns the element language level.
   * <p>
   * Note that it's a rare case when one may need a language level. Usually, it's interesting to check
   * whether a particular language feature is available at a given context. 
   * Consider using {@link #isAvailable(JavaFeature, PsiElement)} instead of this method.
   * 
   * @param element element to get Java language level for
   * @return the language level.
   */
  @NotNull
  public static LanguageLevel getLanguageLevel(@NotNull PsiElement element) {
    if (element instanceof PsiDirectory) {
      return JavaDirectoryService.getInstance().getLanguageLevel((PsiDirectory)element);
    }

    PsiFile file = element.getContainingFile();
    // Could be non-physical 'light file' created by some JVM languages
    PsiFile navigationFile = file == null ? null : ObjectUtils.tryCast(file.getNavigationElement(), PsiFile.class);
    if (navigationFile != null) {
      file = navigationFile;
    }
    if (file instanceof PsiJavaFile) {
      return ((PsiJavaFile)file).getLanguageLevel();
    }

    if (file != null) {
      PsiElement context = file.getContext();
      if (context != null) {
        if (!context.isValid()) {
          throw new PsiInvalidElementAccessException(context, "Invalid context in " + file + " of " + file.getClass());
        }
        return getLanguageLevel(context);
      }
    }

    PsiResolveHelper instance = PsiResolveHelper.getInstance(element.getProject());
    return instance != null ? instance.getEffectiveLanguageLevel(getVirtualFile(file)) : LanguageLevel.HIGHEST;
  }

  @NotNull
  public static LanguageLevel getLanguageLevel(@NotNull Project project) {
    LanguageLevelProjectExtension instance = LanguageLevelProjectExtension.getInstance(project);
    return instance != null ? instance.getLanguageLevel() : LanguageLevel.HIGHEST;
  }

  public static boolean isInstantiatable(@NotNull PsiClass clazz) {
    return !clazz.hasModifierProperty(PsiModifier.ABSTRACT) &&
           clazz.hasModifierProperty(PsiModifier.PUBLIC) &&
           hasDefaultConstructor(clazz);
  }

  public static boolean hasDefaultConstructor(@NotNull PsiClass clazz) {
    return hasDefaultConstructor(clazz, false);
  }

  public static boolean hasDefaultConstructor(@NotNull PsiClass clazz, boolean allowProtected) {
    return hasDefaultConstructor(clazz, allowProtected, false);
  }

  public static boolean hasDefaultConstructor(@NotNull PsiClass clazz, boolean allowProtected, boolean allowPrivateAndPackagePrivate) {
    PsiMethod[] constructors = clazz.getConstructors();
    if (constructors.length == 0) {
      return true;
    }

    for (PsiMethod cls: constructors) {
      if ((cls.hasModifierProperty(PsiModifier.PUBLIC)
           || allowProtected && cls.hasModifierProperty(PsiModifier.PROTECTED)
           || allowPrivateAndPackagePrivate && !cls.hasModifierProperty(PsiModifier.PROTECTED))
          && cls.getParameterList().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Contract("null, _ -> null")
  @Nullable
  public static PsiType extractIterableTypeParameter(@Nullable PsiType psiType, boolean eraseTypeParameter) {
    PsiType type = substituteTypeParameter(psiType, CommonClassNames.JAVA_LANG_ITERABLE, 0, eraseTypeParameter);
    return type != null ? type : substituteTypeParameter(psiType, CommonClassNames.JAVA_UTIL_COLLECTION, 0, eraseTypeParameter);
  }

  @Contract("null, _, _, _ -> null")
  @Nullable
  public static PsiType substituteTypeParameter(@Nullable PsiType psiType, @NotNull String superClass, int typeParamIndex, boolean eraseTypeParameter) {
    PsiClassType.ClassResolveResult classResolveResult = resolveClass(psiType);
    PsiClass psiClass = classResolveResult.getElement();
    if (psiClass == null) return null;

    PsiClass baseClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(superClass, psiClass.getResolveScope());
    if (baseClass == null) return null;

    return substituteType(typeParamIndex, eraseTypeParameter, classResolveResult, psiClass, baseClass);
  }

  @Contract("null, _, _, _ -> null")
  @Nullable
  public static PsiType substituteTypeParameter(@Nullable PsiType psiType, @NotNull PsiClass superClass, int typeParamIndex, boolean eraseTypeParameter) {
    PsiClassType.ClassResolveResult classResolveResult = resolveClass(psiType);
    PsiClass psiClass = classResolveResult.getElement();
    if (psiClass == null) return null;

    return substituteType(typeParamIndex, eraseTypeParameter, classResolveResult, psiClass, superClass);
  }

  private static PsiClassType.ClassResolveResult resolveClass(@Nullable PsiType psiType) {
    return psiType instanceof PsiClassType ? ((PsiClassType)psiType).resolveGenerics() : PsiClassType.ClassResolveResult.EMPTY;
  }

  @Nullable
  private static PsiType substituteType(int typeParamIndex,
                                        boolean eraseTypeParameter,
                                        PsiClassType.ClassResolveResult classResolveResult,
                                        PsiClass psiClass, PsiClass baseClass) {
    if (!psiClass.isEquivalentTo(baseClass) && !psiClass.isInheritor(baseClass, true)) return null;

    PsiTypeParameter[] parameters = baseClass.getTypeParameters();
    if (parameters.length <= typeParamIndex) return PsiType.getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope());

    PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, psiClass, classResolveResult.getSubstitutor());
    PsiType type = substitutor.substitute(parameters[typeParamIndex]);
    if (type == null && eraseTypeParameter) {
      return TypeConversionUtil.typeParameterErasure(parameters[typeParamIndex]);
    }
    return type;
  }

  public static final Comparator<PsiElement> BY_POSITION = (o1, o2) -> compareElementsByPosition(o1, o2);

  public static void setModifierProperty(@NotNull PsiModifierListOwner owner, @NotNull @PsiModifier.ModifierConstant String property, boolean value) {
    PsiModifierList modifierList = owner.getModifierList();
    assert modifierList != null : owner;
    modifierList.setModifierProperty(property, value);
  }

  public static boolean isElseBlock(@Nullable PsiElement element) {
    if (element == null) return false;
    PsiElement parent = element.getParent();
    return parent instanceof PsiIfStatement && element == ((PsiIfStatement)parent).getElseBranch();
  }

  public static boolean isJavaToken(@Nullable PsiElement element, IElementType type) {
    return element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == type;
  }

  public static boolean isJavaToken(@Nullable PsiElement element, @NotNull TokenSet types) {
    return element instanceof PsiJavaToken && types.contains(((PsiJavaToken)element).getTokenType());
  }

  public static boolean isCatchParameter(@Nullable PsiElement element) {
    return element instanceof PsiParameter && element.getParent() instanceof PsiCatchSection;
  }

  public static boolean isIgnoredName(@Nullable String name) {
    return name != null && IGNORED_NAMES.matcher(name).matches();
  }


  public static PsiMethod @Nullable [] getResourceCloserMethodsForType(@NotNull PsiClassType resourceType) {
    PsiClass resourceClass = resourceType.resolve();
    if (resourceClass == null) return null;

    Project project = resourceClass.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass autoCloseable = facade.findClass(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, ProjectScope.getLibrariesScope(project));
    if (autoCloseable == null) return null;

    if (JavaClassSupers.getInstance().getSuperClassSubstitutor(autoCloseable, resourceClass, resourceType.getResolveScope(), PsiSubstitutor.EMPTY) == null) {
      return null;
    }

    PsiMethod[] closes = autoCloseable.findMethodsByName("close", false);
    if (closes.length == 1) {
      return resourceClass.findMethodsBySignature(closes[0], true);
    }
    return null;
  }

  @Contract("null -> null")
  @Nullable
  public static PsiExpression skipParenthesizedExprDown(@Nullable PsiExpression expression) {
    while (expression instanceof PsiParenthesizedExpression) {
      expression = ((PsiParenthesizedExpression)expression).getExpression();
    }
    return expression;
  }

  public static PsiElement skipParenthesizedExprUp(@Nullable PsiElement parent) {
    while (parent instanceof PsiParenthesizedExpression) {
      parent = parent.getParent();
    }
    return parent;
  }

  public static void ensureValidType(@NotNull PsiType type) {
    ensureValidType(type, (String)null);
  }

  public static void ensureValidType(@NotNull PsiType type, @Nullable PsiElement sourceOfType) {
    try {
      ensureValidType(type);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      if (sourceOfType == null) throw e;

      PsiUtilCore.ensureValid(sourceOfType);
      throw new RuntimeException("Via " + sourceOfType.getClass() + " #" + sourceOfType.getLanguage(), e);
    }
  }

  public static void ensureValidType(@NotNull PsiType type, @Nullable String customMessage) {
    if (!type.isValid()) {
      TimeoutUtil.sleep(1); // to see if processing in another thread suddenly makes the type valid again (which is a bug)
      if (type.isValid()) {
        LOG.error("PsiType resurrected: " + type + " of " + type.getClass() + " " + customMessage);
        return;
      }
      if (type instanceof PsiClassType) {
        try {
          PsiClass psiClass = ((PsiClassType)type).resolve(); // should throw exception
          if (psiClass != null) {
            ensureValid(psiClass);
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          if (customMessage == null) {
            throw e;
          }
          throw new RuntimeException(customMessage, e);
        }
      }
      throw new AssertionError("Invalid type: " + type + " of class " + type.getClass() + " " + customMessage);
    }
  }

  @Nullable
  public static String getMemberQualifiedName(@NotNull PsiMember member) {
    if (member instanceof PsiClass) {
      return ((PsiClass)member).getQualifiedName();
    }

    PsiClass containingClass = member.getContainingClass();
    if (containingClass == null) return null;
    String className = containingClass.getQualifiedName();
    if (className == null) return null;
    return className + "." + member.getName();
  }

  public static boolean isFromDefaultPackage(PsiClass aClass) {
    return isFromDefaultPackage((PsiElement)aClass);
  }

  public static boolean isFromDefaultPackage(PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof PsiClassOwner) {
      return StringUtil.isEmpty(((PsiClassOwner)containingFile).getPackageName());
    }

    if (containingFile instanceof JavaCodeFragment) {
      PsiElement context = containingFile.getContext();
      if (context instanceof PsiPackage) {
        return StringUtil.isEmpty(((PsiPackage)context).getName());
      }
      if (context != null && context != containingFile) {
        return isFromDefaultPackage(context);
      }
    }

    return false;
  }

  static boolean checkSameExpression(PsiElement templateExpr, PsiExpression expression) {
    return templateExpr.equals(skipParenthesizedExprDown(expression));
  }

  public static boolean isCondition(PsiElement expr, PsiElement parent) {
    if (parent instanceof PsiIfStatement) {
      return checkSameExpression(expr, ((PsiIfStatement)parent).getCondition());
    }
    if (parent instanceof PsiConditionalLoopStatement) {
      return checkSameExpression(expr, ((PsiConditionalLoopStatement)parent).getCondition());
    }
    if (parent instanceof PsiConditionalExpression) {
      return checkSameExpression(expr, ((PsiConditionalExpression)parent).getCondition());
    }
    return false;
  }

  public static PsiReturnStatement @NotNull [] findReturnStatements(@NotNull PsiMethod method) {
    return findReturnStatements(method.getBody());
  }

  public static PsiReturnStatement @NotNull [] findReturnStatements(@Nullable PsiCodeBlock body) {
    List<PsiReturnStatement> vector = new ArrayList<>();
    if (body != null) {
      addStatements(vector, body, PsiReturnStatement.class, statement -> false);
    }
    return vector.toArray(PsiReturnStatement.EMPTY_ARRAY);
  }

  private static <T extends PsiElement> void addStatements(@NotNull List<? super T> vector,
                                                           @NotNull PsiElement element,
                                                           @NotNull Class<? extends T> clazz,
                                                           @NotNull Predicate<? super PsiElement> stopAt) {
    if (PsiTreeUtil.instanceOf(element, clazz)) {
      //noinspection unchecked
      vector.add((T)element);
    }
    else if (!(element instanceof PsiClass) && !(element instanceof PsiLambdaExpression) && !stopAt.test(element)) {
      PsiElement[] children = element.getChildren();
      for (PsiElement child : children) {
        addStatements(vector, child, clazz, stopAt);
      }
    }
  }

  public static boolean isModuleFile(@NotNull PsiFile file) {
    return file instanceof PsiJavaFile && ((PsiJavaFile)file).getModuleDeclaration() != null;
  }

  public static boolean isPackageEmpty(PsiDirectory @NotNull [] directories, @NotNull String packageName) {
    for (PsiDirectory directory : directories) {
      for (PsiFile file : directory.getFiles()) {
        if (file instanceof PsiClassOwner &&
            packageName.equals(((PsiClassOwner)file).getPackageName()) &&
            ((PsiClassOwner)file).getClasses().length > 0) {
          return false;
        }
      }
    }

    return true;
  }

  @NotNull
  public static PsiModifierListOwner preferCompiledElement(@NotNull PsiModifierListOwner element) {
    PsiElement original = element.getOriginalElement();
    return original instanceof PsiModifierListOwner ? (PsiModifierListOwner)original : element;
  }

  public static PsiElement addModuleStatement(@NotNull PsiJavaModule module, @NotNull String text) {
    PsiJavaParserFacade facade = JavaPsiFacade.getInstance(module.getProject()).getParserFacade();
    PsiStatement statement = facade.createModuleStatementFromText(text, null);
    return addModuleStatement(module, statement);
  }

  public static PsiElement addModuleStatement(@NotNull PsiJavaModule module, @NotNull PsiStatement moduleStatement) {
    final SyntaxTraverser<PsiElement> psiTraverser = SyntaxTraverser.psiTraverser();
    PsiElement anchor = psiTraverser.children(module).filter(moduleStatement.getClass()).last();
    if (anchor == null) {
      anchor = psiTraverser.children(module).filter(e -> isJavaToken(e, JavaTokenType.LBRACE)).first();
    }
    if (anchor == null) {
      final PsiElement moduleReference = psiTraverser.children(module)
        .filter(PsiJavaModuleReferenceElement.class::isInstance).first();

      if (moduleReference != null) {
        PsiJavaParserFacade facade = JavaPsiFacade.getInstance(module.getProject()).getParserFacade();
        final PsiCodeBlock block = facade.createCodeBlockFromText("{}", null);

        final PsiJavaToken lBrace = block.getLBrace();
        final PsiJavaToken rBrace = block.getRBrace();
        if (lBrace != null && rBrace != null) {
          anchor = module.addAfter(lBrace, moduleReference);
          PsiElement rbrace = psiTraverser.children(module).filter(e -> isJavaToken(e, JavaTokenType.RBRACE)).last();
          if (rbrace == null) {
            final PsiElement error = psiTraverser.children(module).filter(PsiErrorElement.class::isInstance).last();
            if (error != null) {
              rbrace = psiTraverser.children(error).filter(e -> isJavaToken(e, JavaTokenType.RBRACE)).last();
            }
          }
          if (rbrace == null) {
            module.add(rBrace);
          }
        } else {
          throw new IllegalStateException("No anchor in " + Arrays.toString(module.getChildren()));
        }
      } else {
        throw new IllegalStateException("No anchor in " + Arrays.toString(module.getChildren()));
      }
    }
    return module.addAfter(moduleStatement, anchor);
  }

  /**
   * @param psiClass element to test
   * @return true if element is a synthetic array class
   * @see PsiElementFactory#isArrayClass(PsiClass) 
   */
  public static boolean isArrayClass(@Nullable PsiElement psiClass) {
    return psiClass instanceof PsiClass && JavaPsiFacade.getElementFactory(psiClass.getProject()).isArrayClass((PsiClass)psiClass);
  }

  /**
   * @param variable variable to test
   * @return true if variable corresponds to JVM local variable defined inside the method
   */
  @Contract("null -> false")
  public static boolean isJvmLocalVariable(PsiElement variable) {
    return variable instanceof PsiLocalVariable || variable instanceof PsiParameter;
  }

  public static boolean isFollowedByImport(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiImportList) {
      final PsiImportList importList = (PsiImportList)parent;
      final PsiImportStatementBase @NotNull [] imports = importList.getAllImportStatements();
      if (imports.length == 0) return false;
      return imports[imports.length - 1].getStartOffsetInParent() > element.getStartOffsetInParent();
    }
    return false;
  }

  private static final Set<String> KEYWORDS = ContainerUtil.immutableSet(
    ABSTRACT, BOOLEAN, BREAK, BYTE, CASE, CATCH, CHAR, CLASS, CONST, CONTINUE, DEFAULT, DO, DOUBLE, ELSE, EXTENDS, FINAL, FINALLY,
    FLOAT, FOR, GOTO, IF, IMPLEMENTS, IMPORT, INSTANCEOF, INT, INTERFACE, LONG, NATIVE, NEW, PACKAGE, PRIVATE, PROTECTED, PUBLIC,
    RETURN, SHORT, STATIC, STRICTFP, SUPER, SWITCH, SYNCHRONIZED, THIS, THROW, THROWS, TRANSIENT, TRY, VOID, VOLATILE, WHILE,
    TRUE, FALSE, NULL, NON_SEALED);

  /**
   * @param id word to check
   * @param level language level
   * @return true if the given word is a keyword at a given level
   */
  public static boolean isKeyword(@NotNull String id, @NotNull LanguageLevel level) {
    return KEYWORDS.contains(id) ||
           JavaFeature.ASSERTIONS.isSufficient(level) && ASSERT.equals(id) ||
           JavaFeature.ENUMS.isSufficient(level) && ENUM.equals(id);
  }

  /**
   * @param id keyword candidate
   * @param level current language level
   * @return true if given id is a soft (restricted) keyword at a given language level
   */
  public static boolean isSoftKeyword(@NotNull CharSequence id, @NotNull LanguageLevel level) {
    JavaFeature feature = softKeywordFeature(id);
    return feature != null && feature.isSufficient(level);
  }

  /**
   * @param keyword soft keyword
   * @return JavaFeature, which introduced a given keyword; null if the supplied string is not a soft keyword 
   */
  public static @Nullable JavaFeature softKeywordFeature(@NotNull CharSequence keyword) {
    return SOFT_KEYWORDS.get(keyword);
  }

  /**
   * @return containing class for {@code element} ignoring {@link PsiAnonymousClass} if {@code element} is located in corresponding expression list
   */
  @Nullable
  public static PsiClass getContainingClass(PsiElement element) {
    PsiClass currentClass;
    while (true) {
      currentClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (currentClass instanceof PsiAnonymousClass &&
          PsiTreeUtil.isAncestor(((PsiAnonymousClass)currentClass).getArgumentList(), element, false)) {
        element = currentClass;
      } else {
        return currentClass;
      }
    }
  }

  /** @return Whether or not the element is part of a markdown javadoc comment */
  @Contract(value = "null -> false", pure = true)
  public static boolean isInMarkdownDocComment(PsiElement element) {
    PsiDocComment docComment = PsiTreeUtil.getParentOfType(element, PsiDocComment.class);
    return docComment != null && docComment.isMarkdownComment();
  }

  //<editor-fold desc="Deprecated stuff">
  /**
   * @deprecated  use {@link #isAvailable(JavaFeature, PsiElement)} instead to check whether a particular feature is available, rather 
   * than to check against a language level; if you still need an explicit language level check, just inline the method call.
   */
  @Deprecated
  public static boolean isLanguageLevel10OrHigher(@NotNull PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_10);
  }

  /**
   * @deprecated use {@link #isAvailable(JavaFeature, PsiElement)} instead to check whether a particular feature is available, rather
   * than to check against a language level; if you still need an explicit language level check, just inline the method call.
   */
  @Deprecated
  public static boolean isLanguageLevel11OrHigher(@NotNull PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_11);
  }

  /**
   * @deprecated use {@link #isAvailable(JavaFeature, PsiElement)} instead to check whether a particular feature is available, rather
   * than to check against a language level; if you still need an explicit language level check, just inline the method call.
   */
  @Deprecated
  public static boolean isLanguageLevel14OrHigher(@NotNull PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_14);
  }

  /**
   * @deprecated use {@link #isAvailable(JavaFeature, PsiElement)} instead to check whether a particular feature is available, rather
   * than to check against a language level; if you still need an explicit language level check, just inline the method call.
   */
  @Deprecated
  public static boolean isLanguageLevel16OrHigher(@NotNull PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_16);
  }

  /**
   * @deprecated use {@link #isAvailable(JavaFeature, PsiElement)} instead to check whether a particular feature is available, rather
   * than to check against a language level; if you still need an explicit language level check, just inline the method call.
   */
  @Deprecated
  public static boolean isLanguageLevel17OrHigher(@NotNull PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_17);
  }

  /**
   * @deprecated use {@link #isAvailable(JavaFeature, PsiElement)} instead to check whether a particular feature is available, rather
   * than to check against a language level; if you still need an explicit language level check, just inline the method call.
   */
  @Deprecated
  public static boolean isLanguageLevel18OrHigher(@NotNull PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_18);
  }
  //</editor-fold>
}