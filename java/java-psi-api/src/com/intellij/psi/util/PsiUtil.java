// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
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
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.EmptyIterable;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.psi.SyntaxTraverser.psiTraverser;

public final class PsiUtil extends PsiUtilCore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.PsiUtil");

  public static final int ACCESS_LEVEL_PUBLIC = 4;
  public static final int ACCESS_LEVEL_PROTECTED = 3;
  public static final int ACCESS_LEVEL_PACKAGE_LOCAL = 2;
  public static final int ACCESS_LEVEL_PRIVATE = 1;
  public static final Key<Boolean> VALID_VOID_TYPE_IN_CODE_FRAGMENT = Key.create("VALID_VOID_TYPE_IN_CODE_FRAGMENT");

  private static final Set<String> IGNORED_NAMES = ContainerUtil.newTroveSet(
    "ignore", "ignore1", "ignore2", "ignore3", "ignore4", "ignore5",
    "ignored", "ignored1", "ignored2", "ignored3", "ignored4", "ignored5");

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
  public static boolean isAccessible(@NotNull Project project, @NotNull PsiMember member,
                                     @NotNull PsiElement place, @Nullable PsiClass accessObjectClass) {
    return JavaPsiFacade.getInstance(project).getResolveHelper().isAccessible(member, place, accessObjectClass);
  }

  @NotNull
  public static JavaResolveResult getAccessObjectClass(@NotNull PsiExpression expression) {
    if (expression instanceof PsiSuperExpression) return JavaResolveResult.EMPTY;
    PsiType type = expression.getType();
    final JavaResolveResult accessObject = getAccessObjectClass(type, expression.getProject());
    if (accessObject != null) return accessObject;

    if (type == null && expression instanceof PsiReferenceExpression) {
      JavaResolveResult resolveResult = ((PsiReferenceExpression)expression).advancedResolve(false);
      if (resolveResult.getElement() instanceof PsiClass) {
        return resolveResult;
      }
    }
    return JavaResolveResult.EMPTY;
  }

  private static JavaResolveResult getAccessObjectClass(PsiType type, Project project) {
    if (type instanceof PsiClassType) {
      return ((PsiClassType)type).resolveGenerics();
    }
    if (type instanceof PsiDisjunctionType) {
      final PsiType lub = ((PsiDisjunctionType)type).getLeastUpperBound();
      if (lub instanceof PsiClassType) {
        return ((PsiClassType)lub).resolveGenerics();
      }
    }

    if (type instanceof PsiCapturedWildcardType) {
      final PsiType upperBound = ((PsiCapturedWildcardType)type).getUpperBound();
      if (upperBound instanceof PsiClassType) {
        final PsiClass resolved = ((PsiClassType)upperBound).resolve();
        final PsiFile containingFile = resolved != null ? resolved.getContainingFile() : null;
        final String packageName = containingFile instanceof PsiClassOwner ? ((PsiClassOwner)containingFile).getPackageName() : null;
        String classText = StringUtil.isEmptyOrSpaces(packageName) ? "" : "package " + packageName + ";\n ";
        classText += "class I<T extends " + upperBound.getCanonicalText() + "> {}";
        final PsiJavaFile file =
          (PsiJavaFile)PsiFileFactory.getInstance(project).createFileFromText("inference_dummy.java", JavaLanguage.INSTANCE, classText);
        final PsiTypeParameter freshParameter = file.getClasses()[0].getTypeParameters()[0];
        return new ClassCandidateInfo(freshParameter, PsiSubstitutor.EMPTY);
      }
    }

    if (type instanceof PsiArrayType) {
      return getAccessObjectClass(((PsiArrayType)type).getComponentType(), project);
    }
    return null;
  }

  public static boolean isConstantExpression(@Nullable PsiExpression expression) {
    return expression != null && JavaPsiFacade.getInstance(expression.getProject()).isConstantExpression(expression);
  }

  // todo: move to PsiThrowsList?
  public static void addException(@NotNull PsiMethod method, @NotNull @NonNls String exceptionFQName) throws IncorrectOperationException {
    PsiClass exceptionClass = JavaPsiFacade.getInstance(method.getProject()).findClass(exceptionFQName, method.getResolveScope());
    addException(method, exceptionClass, exceptionFQName);
  }

  public static void addException(@NotNull PsiMethod method, @NotNull PsiClass exceptionClass) throws IncorrectOperationException {
    addException(method, exceptionClass, exceptionClass.getQualifiedName());
  }

  private static void addException(@NotNull PsiMethod method, @Nullable PsiClass exceptionClass, @Nullable String exceptionName) throws IncorrectOperationException {
    assert exceptionClass != null || exceptionName != null : "One of exceptionName, exceptionClass must be not null";
    PsiReferenceList throwsList = method.getThrowsList();
    PsiJavaCodeReferenceElement[] refs = throwsList.getReferenceElements();
    boolean replaced = false;
    for (PsiJavaCodeReferenceElement ref : refs) {
      if (ref.isReferenceTo(exceptionClass)) return;
      PsiClass aClass = (PsiClass)ref.resolve();
      if (exceptionClass == null || aClass == null) {
        continue;
      }
      if (aClass.isInheritor(exceptionClass, true)) {
        if (replaced) {
          ref.delete();
        }
        else {
          PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
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

    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
    PsiJavaCodeReferenceElement ref;
    if (exceptionName != null) {
      ref = factory.createReferenceElementByFQClassName(exceptionName, method.getResolveScope());
    }
    else {
      PsiClassType type = factory.createType(exceptionClass);
      ref = factory.createReferenceElementByType(type);
    }
    throwsList.add(ref);
  }

  // todo: move to PsiThrowsList?
  public static void removeException(@NotNull PsiMethod method, @NonNls String exceptionClass) throws IncorrectOperationException {
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
  public static PsiElement getVariableCodeBlock(@NotNull PsiVariable variable, @Nullable PsiElement context) {
    PsiElement codeBlock = null;
    if (variable instanceof PsiParameter) {
      PsiElement declarationScope = ((PsiParameter)variable).getDeclarationScope();
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
    }
    else if (variable instanceof PsiResourceVariable) {
      final PsiElement resourceList = variable.getParent();
      return resourceList != null ? resourceList.getParent() : null;  // use try statement as topmost
    }
    else if (variable instanceof PsiLocalVariable && variable.getParent() instanceof PsiForStatement) {
      return variable.getParent();
    }
    else if (variable instanceof PsiField && context != null) {
      final PsiClass aClass = ((PsiField) variable).getContainingClass();
      while (context != null && context.getParent() != aClass) {
        context = context.getParent();
        if (context instanceof PsiClassLevelDeclarationStatement) return null;
      }
      return context instanceof PsiMethod ?
             ((PsiMethod) context).getBody() :
             context instanceof PsiClassInitializer ? ((PsiClassInitializer) context).getBody() : null;
    }
    else {
      final PsiElement scope = variable.getParent() == null ? null : variable.getParent().getParent();
      codeBlock = getTopLevelEnclosingCodeBlock(variable, scope);
      if (codeBlock != null && codeBlock.getParent() instanceof PsiSwitchStatement) codeBlock = codeBlock.getParent().getParent();
    }
    return codeBlock;
  }

  @Contract("null -> false")
  public static boolean isIncrementDecrementOperation(@Nullable PsiElement element) {
    if (element instanceof PsiUnaryExpression) {
      final IElementType sign = ((PsiUnaryExpression)element).getOperationTokenType();
      return sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS;
    }
    return false;
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

  @PsiModifier.ModifierConstant
  @NotNull
  public static String getAccessModifier(@AccessLevel int accessLevel) {
    assert accessLevel > 0 && accessLevel <= accessModifiers.length : accessLevel;
    @SuppressWarnings("UnnecessaryLocalVariable") @PsiModifier.ModifierConstant
    final String modifier =  accessModifiers[accessLevel - 1];
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
      final PsiForStatement forStatement = (PsiForStatement)parent;
      if (!(element == forStatement.getInitialization() || element == forStatement.getUpdate())) return false;
      final PsiExpressionList expressionList = ((PsiExpressionListStatement) element).getExpressionList();
      final PsiExpression[] expressions = expressionList.getExpressions();
      for (PsiExpression expression : expressions) {
        if (!isStatement(expression)) return false;
      }
      return true;
    }
    else if (element instanceof PsiExpressionStatement) {
      return isStatement(((PsiExpressionStatement) element).getExpression());
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
      return !(((PsiNewExpression) element).getType() instanceof PsiArrayType);
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
      final PsiType lub = ((PsiDisjunctionType)type).getLeastUpperBound();
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
      final PsiClassType classType = (PsiClassType) type;
      return classType.resolveGenerics();
    }
    if (type instanceof PsiArrayType) {
      return resolveGenericsClassInType(((PsiArrayType) type).getComponentType());
    }
    if (type instanceof PsiDisjunctionType) {
      final PsiType lub = ((PsiDisjunctionType)type).getLeastUpperBound();
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

  public static boolean isApplicable(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutorForMethod, @NotNull PsiExpressionList argList) {
    return getApplicabilityLevel(method, substitutorForMethod, argList) != ApplicabilityLevel.NOT_APPLICABLE;
  }

  public static boolean isApplicable(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutorForMethod, @NotNull PsiExpression[] argList) {
    final PsiType[] types = ContainerUtil.map2Array(argList, PsiType.class, PsiExpression.EXPRESSION_TO_TYPE);
    return getApplicabilityLevel(method, substitutorForMethod, types, getLanguageLevel(method)) != ApplicabilityLevel.NOT_APPLICABLE;
  }

  @MethodCandidateInfo.ApplicabilityLevelConstant
  public static int getApplicabilityLevel(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutorForMethod, @NotNull PsiExpressionList argList) {
    return getApplicabilityLevel(method, substitutorForMethod, argList.getExpressionTypes(), getLanguageLevel(argList));
  }

  @MethodCandidateInfo.ApplicabilityLevelConstant
  public static int getApplicabilityLevel(@NotNull final PsiMethod method,
                                          @NotNull final PsiSubstitutor substitutorForMethod,
                                          @NotNull final PsiType[] args,
                                          @NotNull final LanguageLevel languageLevel) {
    return getApplicabilityLevel(method, substitutorForMethod, args, languageLevel, true, true);
  }


  public interface ApplicabilityChecker {
    ApplicabilityChecker ASSIGNABILITY_CHECKER = new ApplicabilityChecker() {
      @Override
      public boolean isApplicable(PsiType left, PsiType right, boolean allowUncheckedConversion, int argId) {
        return TypeConversionUtil.isAssignable(left, right, allowUncheckedConversion);
      }
    };

    boolean isApplicable(PsiType left, PsiType right, boolean allowUncheckedConversion, int argId);
  }

  @MethodCandidateInfo.ApplicabilityLevelConstant
  public static int getApplicabilityLevel(@NotNull final PsiMethod method,
                                          @NotNull final PsiSubstitutor substitutorForMethod,
                                          @NotNull final PsiType[] args,
                                          @NotNull final LanguageLevel languageLevel,
                                          final boolean allowUncheckedConversion,
                                          final boolean checkVarargs) {
    return getApplicabilityLevel(method, substitutorForMethod, args, languageLevel,
                                 allowUncheckedConversion, checkVarargs, ApplicabilityChecker.ASSIGNABILITY_CHECKER);
  }

  @MethodCandidateInfo.ApplicabilityLevelConstant
  public static int getApplicabilityLevel(@NotNull final PsiMethod method,
                                          @NotNull final PsiSubstitutor substitutorForMethod,
                                          @NotNull final PsiType[] args,
                                          @NotNull final LanguageLevel languageLevel,
                                                   final boolean allowUncheckedConversion,
                                                   final boolean checkVarargs,
                                          @NotNull final ApplicabilityChecker function) {
    final PsiParameter[] parms = method.getParameterList().getParameters();
    if (args.length < parms.length - 1) return ApplicabilityLevel.NOT_APPLICABLE;

    final PsiClass containingClass = method.getContainingClass();
    final boolean isRaw = containingClass != null && isRawSubstitutor(method, substitutorForMethod) && isRawSubstitutor(containingClass, substitutorForMethod);
    if (!areFirstArgumentsApplicable(args, parms, languageLevel, substitutorForMethod, isRaw, allowUncheckedConversion, function)) return ApplicabilityLevel.NOT_APPLICABLE;
    if (args.length == parms.length) {
      if (parms.length == 0) return ApplicabilityLevel.FIXED_ARITY;
      PsiType parmType = getParameterType(parms[parms.length - 1], languageLevel, substitutorForMethod);
      PsiType argType = args[args.length - 1];
      if (argType == null) return ApplicabilityLevel.NOT_APPLICABLE;
      if (function.isApplicable(parmType, argType, allowUncheckedConversion, parms.length - 1)) return ApplicabilityLevel.FIXED_ARITY;

      if (isRaw) {
        final PsiType erasedParamType = TypeConversionUtil.erasure(parmType);
        if (erasedParamType != null && function.isApplicable(erasedParamType, argType, allowUncheckedConversion, parms.length - 1)) {
          return ApplicabilityLevel.FIXED_ARITY;
        }
      }
    }

    if (checkVarargs && method.isVarArgs() && languageLevel.compareTo(LanguageLevel.JDK_1_5) >= 0) {
      if (args.length < parms.length) return ApplicabilityLevel.VARARGS;
      PsiParameter lastParameter = parms.length == 0 ? null : parms[parms.length - 1];
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
      for (int i = parms.length - 1; i < args.length; i++) {
        PsiType argType = args[i];
        if (argType == null || !function.isApplicable(lastParmType, argType, allowUncheckedConversion, i)) {
          return ApplicabilityLevel.NOT_APPLICABLE;
        }
      }
      return ApplicabilityLevel.VARARGS;
    }

    return ApplicabilityLevel.NOT_APPLICABLE;
  }

  private static boolean areFirstArgumentsApplicable(@NotNull PsiType[] args,
                                                     @NotNull final PsiParameter[] parms,
                                                     @NotNull LanguageLevel languageLevel,
                                                     @NotNull final PsiSubstitutor substitutorForMethod,
                                                     boolean isRaw,
                                                     boolean allowUncheckedConversion, ApplicabilityChecker function) {
    for (int i = 0; i < parms.length - 1; i++) {
      final PsiType type = args[i];
      if (type == null) return false;
      final PsiParameter parameter = parms[i];
      final PsiType substitutedParmType = getParameterType(parameter, languageLevel, substitutorForMethod);
      if (isRaw) {
        final PsiType substErasure = TypeConversionUtil.erasure(substitutedParmType);
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

  private static PsiType getParameterType(@NotNull final PsiParameter parameter,
                                          @NotNull LanguageLevel languageLevel,
                                          @NotNull final PsiSubstitutor substitutor) {
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
  public static boolean equalOnEquivalentClasses(PsiClassType thisClassType,
                                                 @NotNull PsiClass aClass,
                                                 PsiClassType otherClassType,
                                                 @NotNull PsiClass bClass) {
    final PsiClassType capture1 = !PsiCapturedWildcardType.isCapture()
                                  ? thisClassType : (PsiClassType)captureToplevelWildcards(thisClassType, aClass);
    final PsiClassType capture2 = !PsiCapturedWildcardType.isCapture()
                                  ? otherClassType : (PsiClassType)captureToplevelWildcards(otherClassType, bClass);

    final PsiClassType.ClassResolveResult result1 = capture1.resolveGenerics();
    final PsiClassType.ClassResolveResult result2 = capture2.resolveGenerics();

    return equalOnEquivalentClasses(result1.getSubstitutor(), aClass, result2.getSubstitutor(), bClass);
  }

  private static boolean equalOnEquivalentClasses(@NotNull PsiSubstitutor s1,
                                                  @NotNull PsiClass aClass,
                                                  @NotNull PsiSubstitutor s2,
                                                  @NotNull PsiClass bClass) {
    // assume generic class equals to non-generic
    if (aClass.hasTypeParameters() != bClass.hasTypeParameters()) return true;
    final PsiTypeParameter[] typeParameters1 = aClass.getTypeParameters();
    final PsiTypeParameter[] typeParameters2 = bClass.getTypeParameters();
    if (typeParameters1.length != typeParameters2.length) return false;
    for (int i = 0; i < typeParameters1.length; i++) {
      final PsiType substituted2 = s2.substitute(typeParameters2[i]);
      final PsiType substituted1 = s1.substitute(typeParameters1[i]);
      if (!Comparing.equal(substituted1, substituted2)) return false;
    }
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) return true;
    final PsiClass containingClass1 = aClass.getContainingClass();
    final PsiClass containingClass2 = bClass.getContainingClass();

    if (containingClass1 != null && containingClass2 != null) {
      return equalOnEquivalentClasses(s1, containingClass1, s2, containingClass2);
    }

    return containingClass1 == null && containingClass2 == null;
  }

  /** @deprecated use more generic {@link #isCompileTimeConstant(PsiVariable)} instead */
  public static boolean isCompileTimeConstant(@NotNull final PsiField field) {
    return isCompileTimeConstant((PsiVariable)field);
  }

  /**
   * JLS 15.28
   */
  public static boolean isCompileTimeConstant(@NotNull final PsiVariable field) {
    return field.hasModifierProperty(PsiModifier.FINAL)
           && (TypeConversionUtil.isPrimitiveAndNotNull(field.getType()) || field.getType().equalsToText(JAVA_LANG_STRING))
           && field.hasInitializer()
           && isConstantExpression(field.getInitializer());
  }

  public static boolean allMethodsHaveSameSignature(@NotNull PsiMethod[] methods) {
    if (methods.length == 0) return true;
    final MethodSignature methodSignature = methods[0].getSignature(PsiSubstitutor.EMPTY);
    for (int i = 1; i < methods.length; i++) {
      PsiMethod method = methods[i];
      if (!methodSignature.equals(method.getSignature(PsiSubstitutor.EMPTY))) return false;
    }
    return true;
  }

  @Nullable
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
  public static PsiElement findModifierInList(@NotNull final PsiModifierList modifierList, @NonNls String modifier) {
    final PsiElement[] children = modifierList.getChildren();
    for (PsiElement child : children) {
      if (child.getText().equals(modifier)) return child;
    }
    return null;
  }

  @Nullable
  public static PsiClass getTopLevelClass(@NotNull PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (file instanceof PsiClassOwner) {
      final PsiClass[] classes = ((PsiClassOwner)file).getClasses();
      for (PsiClass aClass : classes) {
        if (PsiTreeUtil.isAncestor(aClass, element, false)) return aClass;
      }
    }
    return null;
  }

  /**
   * @param place place to start traversal
   * @param aClass level to stop traversal
   * @return element with static modifier enclosing place and enclosed by aClass (if not null)
   */
  @Nullable
  public static PsiModifierListOwner getEnclosingStaticElement(@NotNull PsiElement place, @Nullable PsiClass aClass) {
    LOG.assertTrue(aClass == null || !place.isPhysical() || PsiTreeUtil.isContextAncestor(aClass, place, false));
    PsiElement parent = place;
    while (parent != aClass) {
      if (parent instanceof PsiFile) break;
      if (parent instanceof PsiModifierListOwner && ((PsiModifierListOwner)parent).hasModifierProperty(PsiModifier.STATIC)) {
        return (PsiModifierListOwner)parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  @Nullable
  public static PsiType getTypeByPsiElement(@NotNull final PsiElement element) {
    if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getType();
    }
    else if (element instanceof PsiMethod) return ((PsiMethod)element).getReturnType();
    return null;
  }

  /**
   * Applies capture conversion to the type in context
   */
  @NotNull
  public static PsiType captureToplevelWildcards(@NotNull final PsiType type, @NotNull final PsiElement context) {
    if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
      final PsiClass aClass = result.getElement();
      if (aClass != null) {
        final PsiSubstitutor substitutor = result.getSubstitutor();

        PsiSubstitutor captureSubstitutor = substitutor;
        for (PsiTypeParameter typeParameter : typeParametersIterable(aClass)) {
          final PsiType substituted = substitutor.substitute(typeParameter);
          if (substituted instanceof PsiWildcardType) {
            captureSubstitutor = captureSubstitutor.put(typeParameter, PsiCapturedWildcardType.create((PsiWildcardType)substituted, context, typeParameter));
          }
        }

        if (captureSubstitutor != substitutor) {
          Map<PsiTypeParameter, PsiType> substitutionMap = null;
          for (PsiTypeParameter typeParameter : typeParametersIterable(aClass)) {
            final PsiType substituted = substitutor.substitute(typeParameter);
            if (substituted instanceof PsiWildcardType) {
              if (substitutionMap == null) substitutionMap = new HashMap<>(substitutor.getSubstitutionMap());
              final PsiCapturedWildcardType capturedWildcard = (PsiCapturedWildcardType)captureSubstitutor.substitute(typeParameter);
              LOG.assertTrue(capturedWildcard != null);
              final PsiType upperBound = PsiCapturedWildcardType.captureUpperBound(typeParameter, (PsiWildcardType)substituted, captureSubstitutor);
              if (upperBound != null) {
                capturedWildcard.setUpperBound(upperBound);
              }
              substitutionMap.put(typeParameter, capturedWildcard);
            }
          }

          if (substitutionMap != null) {
            final PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
            final PsiSubstitutor newSubstitutor = factory.createSubstitutor(substitutionMap);
            return factory.createType(aClass, newSubstitutor);
          }
        }
      }
    }
    return type;
  }

  /**
   * Opens top level captured wildcards and remap them according to the context.
   * The only valid purpose: allow to speculate on non-physical expressions about types, e.g. to detect redundant casts with 'wildcards'
   */
  public static PsiType recaptureWildcards(PsiType type, PsiElement context) {
    if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass != null) {
        final PsiSubstitutor substitutor = resolveResult.getSubstitutor();

        PsiSubstitutor resultSubstitution = null;
        for (PsiTypeParameter parameter : substitutor.getSubstitutionMap().keySet()) {
          final PsiType substitute = substitutor.substitute(parameter);
          if (substitute instanceof PsiCapturedWildcardType) {
            if (resultSubstitution == null) resultSubstitution = substitutor;
            resultSubstitution = resultSubstitution.put(parameter, ((PsiCapturedWildcardType)substitute).getWildcard());
          }
        }

        if (resultSubstitution != null) {
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
          return captureToplevelWildcards(factory.createType(aClass, resultSubstitution), context);
        }
      }
    }
    else if (type instanceof PsiArrayType) {
      return recaptureWildcards(((PsiArrayType)type).getComponentType(), context).createArrayType();
    }
    return type;
  }

  public static boolean isInsideJavadocComment(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiDocComment.class, true) != null;
  }

  @NotNull
  public static List<PsiTypeElement> getParameterTypeElements(@NotNull PsiParameter parameter) {
    PsiTypeElement typeElement = parameter.getTypeElement();
    return typeElement != null && typeElement.getType() instanceof PsiDisjunctionType
           ? PsiTreeUtil.getChildrenOfTypeAsList(typeElement, PsiTypeElement.class)
           : Collections.singletonList(typeElement);
  }

  public static void checkIsIdentifier(@NotNull PsiManager manager, String text) throws IncorrectOperationException{
    if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(text)){
      throw new IncorrectOperationException(PsiBundle.message("0.is.not.an.identifier", text) );
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

  @PsiModifier.ModifierConstant
  public static String getMaximumModifierForMember(final PsiClass aClass, boolean allowPublicAbstract) {
    String modifier = PsiModifier.PUBLIC;

    if (!allowPublicAbstract && aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !aClass.isEnum()) {
      modifier =  PsiModifier.PROTECTED;
    }
    else if (aClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) || aClass.isEnum()) {
      modifier = PsiModifier.PACKAGE_LOCAL;
    }
    else if (aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      modifier = PsiModifier.PRIVATE;
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
  public static Iterable<PsiTypeParameter> typeParametersIterable(@NotNull final PsiTypeParameterListOwner owner) {
    List<PsiTypeParameter> result = null;

    PsiTypeParameterListOwner currentOwner = owner;
    while (currentOwner != null) {
      PsiTypeParameter[] typeParameters = currentOwner.getTypeParameters();
      if (typeParameters.length > 0) {
        if (result == null) result = new ArrayList<>(typeParameters.length);
        for (int i = typeParameters.length - 1; i >= 0; i--) {
          result.add(typeParameters[i]);
        }
      }

      if (currentOwner.hasModifierProperty(PsiModifier.STATIC)) break;
      currentOwner = currentOwner.getContainingClass();
    }

    if (result == null) return EmptyIterable.getInstance();
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

  /**
   * @deprecated Use {@link #canBeOverridden(PsiMethod)} instead
   */
  public static boolean canBeOverriden(@NotNull PsiMethod method) {
    return canBeOverridden(method);
  }

  @NotNull
  public static PsiElement[] mapElements(@NotNull ResolveResult[] candidates) {
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
    if (parent instanceof PsiField && parent.hasModifierProperty(PsiModifier.STATIC)) return null;
    return parent;
  }

  public static boolean checkName(@NotNull PsiElement element, @NotNull String name, final PsiElement context) {
    if (element instanceof PsiMetaOwner) {
      final PsiMetaData data = ((PsiMetaOwner) element).getMetaData();
      if (data != null) return name.equals(data.getName(context));
    }
    return element instanceof PsiNamedElement && name.equals(((PsiNamedElement)element).getName());
  }

  public static boolean isRawSubstitutor(@NotNull PsiTypeParameterListOwner owner, @NotNull PsiSubstitutor substitutor) {
    if (substitutor == PsiSubstitutor.EMPTY) return false;

    for (PsiTypeParameter parameter : typeParametersIterable(owner)) {
      if (substitutor.substitute(parameter) == null) return true;
    }
    return false;
  }

  public static final Key<LanguageLevel> FILE_LANGUAGE_LEVEL_KEY = Key.create("FORCE_LANGUAGE_LEVEL");

  public static boolean isLanguageLevel5OrHigher(@NotNull final PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_5);
  }

  public static boolean isLanguageLevel6OrHigher(@NotNull final PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_6);
  }

  public static boolean isLanguageLevel7OrHigher(@NotNull final PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_7);
  }

  public static boolean isLanguageLevel8OrHigher(@NotNull final PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_8);
  }

  public static boolean isLanguageLevel9OrHigher(@NotNull final PsiElement element) {
    return getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_9);
  }

  @NotNull
  public static LanguageLevel getLanguageLevel(@NotNull PsiElement element) {
    if (element instanceof PsiDirectory) {
      return JavaDirectoryService.getInstance().getLanguageLevel((PsiDirectory)element);
    }

    PsiFile file = element.getContainingFile();
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

    PsiResolveHelper instance = PsiResolveHelper.SERVICE.getInstance(element.getProject());
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
    return hasDefaultConstructor(clazz, allowProtected, true);
  }

  public static boolean hasDefaultConstructor(@NotNull PsiClass clazz, boolean allowProtected, boolean checkModifiers) {
    return hasDefaultCtrInHierarchy(clazz, allowProtected, checkModifiers, null);
  }

  private static boolean hasDefaultCtrInHierarchy(@NotNull PsiClass clazz, boolean allowProtected, boolean checkModifiers, @Nullable Set<PsiClass> visited) {
    final PsiMethod[] constructors = clazz.getConstructors();
    if (constructors.length > 0) {
      for (PsiMethod cls: constructors) {
        if ((!checkModifiers || cls.hasModifierProperty(PsiModifier.PUBLIC) ||
             allowProtected && cls.hasModifierProperty(PsiModifier.PROTECTED)) &&
            cls.getParameterList().getParametersCount() == 0) {
          return true;
        }
      }
    }
    else {
      final PsiClass superClass = clazz.getSuperClass();
      if (superClass == null) {
        return true;
      }
      if (visited == null) visited = new THashSet<>();
      if (!visited.add(clazz)) return false;
      return hasDefaultCtrInHierarchy(superClass, true, true, visited);
    }
    return false;
  }

  @Contract("null, _ -> null")
  @Nullable
  public static PsiType extractIterableTypeParameter(@Nullable PsiType psiType, final boolean eraseTypeParameter) {
    final PsiType type = substituteTypeParameter(psiType, CommonClassNames.JAVA_LANG_ITERABLE, 0, eraseTypeParameter);
    return type != null ? type : substituteTypeParameter(psiType, CommonClassNames.JAVA_UTIL_COLLECTION, 0, eraseTypeParameter);
  }

  @Contract("null, _, _, _ -> null")
  @Nullable
  public static PsiType substituteTypeParameter(@Nullable final PsiType psiType, @NotNull final String superClass, final int typeParamIndex,
                                                final boolean eraseTypeParameter) {
    if (psiType == null) return null;

    if (!(psiType instanceof PsiClassType)) return null;

    final PsiClassType classType = (PsiClassType)psiType;
    final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
    final PsiClass psiClass = classResolveResult.getElement();
    if (psiClass == null) return null;

    final PsiClass baseClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(superClass, psiClass.getResolveScope());
    if (baseClass == null) return null;

    return substituteType(typeParamIndex, eraseTypeParameter, classResolveResult, psiClass, baseClass);
  }

  @Contract("null, _, _, _ -> null")
  @Nullable
  public static PsiType substituteTypeParameter(@Nullable final PsiType psiType, @NotNull final PsiClass superClass, final int typeParamIndex,
                                                final boolean eraseTypeParameter) {
    if (psiType == null) return null;

    if (!(psiType instanceof PsiClassType)) return null;

    final PsiClassType classType = (PsiClassType)psiType;
    final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
    final PsiClass psiClass = classResolveResult.getElement();
    if (psiClass == null) return null;

    return substituteType(typeParamIndex, eraseTypeParameter, classResolveResult, psiClass, superClass);
  }

  @Nullable
  private static PsiType substituteType(int typeParamIndex,
                                        boolean eraseTypeParameter,
                                        PsiClassType.ClassResolveResult classResolveResult,
                                        PsiClass psiClass, PsiClass baseClass) {
    if (!psiClass.isEquivalentTo(baseClass) && !psiClass.isInheritor(baseClass, true)) return null;

    final PsiTypeParameter[] parameters = baseClass.getTypeParameters();
    if (parameters.length <= typeParamIndex) return PsiType.getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope());

    final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, psiClass, classResolveResult.getSubstitutor());
    final PsiType type = substitutor.substitute(parameters[typeParamIndex]);
    if (type == null && eraseTypeParameter) {
      return TypeConversionUtil.typeParameterErasure(parameters[typeParamIndex]);
    }
    return type;
  }

  public static final Comparator<PsiElement> BY_POSITION = (o1, o2) -> compareElementsByPosition(o1, o2);

  public static void setModifierProperty(@NotNull PsiModifierListOwner owner, @NotNull @PsiModifier.ModifierConstant String property, boolean value) {
    final PsiModifierList modifierList = owner.getModifierList();
    assert modifierList != null : owner;
    modifierList.setModifierProperty(property, value);
  }

  public static boolean isTryBlock(@Nullable final PsiElement element) {
    if (element == null) return false;
    final PsiElement parent = element.getParent();
    return parent instanceof PsiTryStatement && element == ((PsiTryStatement)parent).getTryBlock();
  }

  public static boolean isElseBlock(@Nullable final PsiElement element) {
    if (element == null) return false;
    final PsiElement parent = element.getParent();
    return parent instanceof PsiIfStatement && element == ((PsiIfStatement)parent).getElseBranch();
  }

  public static boolean isJavaToken(@Nullable PsiElement element, IElementType type) {
    return element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == type;
  }

  public static boolean isJavaToken(@Nullable PsiElement element, @NotNull TokenSet types) {
    return element instanceof PsiJavaToken && types.contains(((PsiJavaToken)element).getTokenType());
  }

  public static boolean isCatchParameter(@Nullable final PsiElement element) {
    return element instanceof PsiParameter && element.getParent() instanceof PsiCatchSection;
  }

  public static boolean isIgnoredName(@Nullable String name) {
    return name != null && IGNORED_NAMES.contains(name);
  }


  @Nullable
  public static PsiMethod[] getResourceCloserMethodsForType(@NotNull final PsiClassType resourceType) {
    final PsiClass resourceClass = resourceType.resolve();
    if (resourceClass == null) return null;

    final Project project = resourceClass.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass autoCloseable = facade.findClass(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, ProjectScope.getLibrariesScope(project));
    if (autoCloseable == null) return null;

    if (JavaClassSupers.getInstance().getSuperClassSubstitutor(autoCloseable, resourceClass, resourceType.getResolveScope(), PsiSubstitutor.EMPTY) == null) return null;

    final PsiMethod[] closes = autoCloseable.findMethodsByName("close", false);
    if (closes.length == 1) {
      return resourceClass.findMethodsBySignature(closes[0], true);
    }
    return null;
  }

  @Nullable
  public static PsiExpression skipParenthesizedExprDown(PsiExpression initializer) {
    while (initializer instanceof PsiParenthesizedExpression) {
      initializer = ((PsiParenthesizedExpression)initializer).getExpression();
    }
    return initializer;
  }

  public static PsiElement skipParenthesizedExprUp(PsiElement parent) {
    while (parent instanceof PsiParenthesizedExpression) {
      parent = parent.getParent();
    }
    return parent;
  }

  public static void ensureValidType(@NotNull PsiType type) {
    ensureValidType(type, null);
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
        catch (PsiInvalidElementAccessException e) {
          throw customMessage == null? e : new RuntimeException(customMessage, e);
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
    final PsiFile containingFile = element.getContainingFile();
    return containingFile instanceof PsiClassOwner && StringUtil.isEmpty(((PsiClassOwner)containingFile).getPackageName());
  }

  static boolean checkSameExpression(PsiElement templateExpr, final PsiExpression expression) {
    return templateExpr.equals(skipParenthesizedExprDown(expression));
  }

  public static boolean isCondition(PsiElement expr, PsiElement parent) {
    if (parent instanceof PsiIfStatement) {
      if (checkSameExpression(expr, ((PsiIfStatement)parent).getCondition())) {
        return true;
      }
    }
    else if (parent instanceof PsiWhileStatement) {
      if (checkSameExpression(expr, ((PsiWhileStatement)parent).getCondition())) {
        return true;
      }
    }
    else if (parent instanceof PsiForStatement) {
      if (checkSameExpression(expr, ((PsiForStatement)parent).getCondition())) {
        return true;
      }
    }
    else if (parent instanceof PsiDoWhileStatement) {
      if (checkSameExpression(expr, ((PsiDoWhileStatement)parent).getCondition())) {
        return true;
      }
    }
    else if (parent instanceof PsiConditionalExpression) {
      if (checkSameExpression(expr, ((PsiConditionalExpression)parent).getCondition())) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static PsiReturnStatement[] findReturnStatements(@NotNull PsiMethod method) {
    return findReturnStatements(method.getBody());
  }

  @NotNull
  public static PsiReturnStatement[] findReturnStatements(@Nullable PsiCodeBlock body) {
    ArrayList<PsiReturnStatement> vector = new ArrayList<>();
    if (body != null) {
      addReturnStatements(vector, body);
    }
    return vector.toArray(new PsiReturnStatement[vector.size()]);
  }

  private static void addReturnStatements(ArrayList<PsiReturnStatement> vector, PsiElement element) {
    if (element instanceof PsiReturnStatement) {
      vector.add((PsiReturnStatement)element);
    }
    else if (!(element instanceof PsiClass) && !(element instanceof PsiLambdaExpression)) {
      PsiElement[] children = element.getChildren();
      for (PsiElement child : children) {
        addReturnStatements(vector, child);
      }
    }
  }

  public static boolean isModuleFile(@NotNull PsiFile file) {
    return file instanceof PsiJavaFile && ((PsiJavaFile)file).getModuleDeclaration() != null;
  }

  public static boolean isPackageEmpty(@NotNull PsiDirectory[] directories, @NotNull String packageName) {
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
    PsiStatement statement = facade.createModuleStatementFromText(text);

    PsiElement anchor = psiTraverser().children(module).filter(statement.getClass()).last();
    if (anchor == null) {
      anchor = psiTraverser().children(module).filter(e -> isJavaToken(e, JavaTokenType.LBRACE)).first();
    }
    if (anchor == null) {
      throw new IllegalStateException("No anchor in " + Arrays.toString(module.getChildren()));
    }

    return module.addAfter(statement, anchor);
  }
}