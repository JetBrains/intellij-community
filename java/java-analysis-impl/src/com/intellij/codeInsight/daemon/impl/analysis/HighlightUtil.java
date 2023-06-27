// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.ContainerProvider;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.JavaModuleSystemEx;
import com.intellij.codeInsight.JavaModuleSystemEx.ErrorWithFixes;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater;
import com.intellij.codeInspection.dataFlow.fix.RedundantInstanceofFix;
import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.JavaLanguageLevelPusher;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.tryCast;

// generates HighlightInfoType.ERROR-like HighlightInfos
public final class HighlightUtil {
  public static final Set<String> RESTRICTED_RECORD_COMPONENT_NAMES = Set.of(
    "clone", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait");

  private static final Logger LOG = Logger.getInstance(HighlightUtil.class);

  private static final Map<String, Set<String>> ourInterfaceIncompatibleModifiers = new HashMap<>(7);
  private static final Map<String, Set<String>> ourMethodIncompatibleModifiers = new HashMap<>(11);
  private static final Map<String, Set<String>> ourFieldIncompatibleModifiers = new HashMap<>(8);
  private static final Map<String, Set<String>> ourClassIncompatibleModifiers = new HashMap<>(8);
  private static final Map<String, Set<String>> ourClassInitializerIncompatibleModifiers = new HashMap<>(1);
  private static final Map<String, Set<String>> ourModuleIncompatibleModifiers = new HashMap<>(1);
  private static final Map<String, Set<String>> ourRequiresIncompatibleModifiers = new HashMap<>(2);

  private static final Set<String> ourConstructorNotAllowedModifiers =
    Set.of(PsiModifier.ABSTRACT, PsiModifier.STATIC, PsiModifier.NATIVE, PsiModifier.FINAL, PsiModifier.STRICTFP, PsiModifier.SYNCHRONIZED);

  private static final String SERIAL_PERSISTENT_FIELDS_FIELD_NAME = "serialPersistentFields";
  public static final TokenSet BRACKET_TOKENS = TokenSet.create(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET);

  static {
    ourClassIncompatibleModifiers.put(PsiModifier.ABSTRACT, Set.of(PsiModifier.FINAL));
    ourClassIncompatibleModifiers.put(PsiModifier.FINAL, Set.of(PsiModifier.ABSTRACT, PsiModifier.SEALED, PsiModifier.NON_SEALED));
    ourClassIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, Set.of(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourClassIncompatibleModifiers.put(PsiModifier.PRIVATE, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourClassIncompatibleModifiers.put(PsiModifier.PUBLIC, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED));
    ourClassIncompatibleModifiers.put(PsiModifier.PROTECTED, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE));
    ourClassIncompatibleModifiers.put(PsiModifier.STRICTFP, Set.of());
    ourClassIncompatibleModifiers.put(PsiModifier.STATIC, Set.of());
    ourClassIncompatibleModifiers.put(PsiModifier.SEALED, Set.of(PsiModifier.FINAL, PsiModifier.NON_SEALED));
    ourClassIncompatibleModifiers.put(PsiModifier.NON_SEALED, Set.of(PsiModifier.FINAL, PsiModifier.SEALED));

    ourInterfaceIncompatibleModifiers.put(PsiModifier.ABSTRACT, Set.of());
    ourInterfaceIncompatibleModifiers
      .put(PsiModifier.PACKAGE_LOCAL, Set.of(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourInterfaceIncompatibleModifiers
      .put(PsiModifier.PRIVATE, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourInterfaceIncompatibleModifiers
      .put(PsiModifier.PUBLIC, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED));
    ourInterfaceIncompatibleModifiers
      .put(PsiModifier.PROTECTED, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE));
    ourInterfaceIncompatibleModifiers.put(PsiModifier.STRICTFP, Set.of());
    ourInterfaceIncompatibleModifiers.put(PsiModifier.STATIC, Set.of());
    ourInterfaceIncompatibleModifiers.put(PsiModifier.SEALED, Set.of(PsiModifier.NON_SEALED));
    ourInterfaceIncompatibleModifiers.put(PsiModifier.NON_SEALED, Set.of(PsiModifier.SEALED));

    ourMethodIncompatibleModifiers.put(PsiModifier.ABSTRACT, Set.of(
      PsiModifier.NATIVE, PsiModifier.STATIC, PsiModifier.FINAL, PsiModifier.PRIVATE, PsiModifier.STRICTFP, PsiModifier.SYNCHRONIZED,
      PsiModifier.DEFAULT));
    ourMethodIncompatibleModifiers.put(PsiModifier.NATIVE, Set.of(PsiModifier.ABSTRACT, PsiModifier.STRICTFP));
    ourMethodIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, Set.of(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourMethodIncompatibleModifiers.put(PsiModifier.PRIVATE, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourMethodIncompatibleModifiers.put(PsiModifier.PUBLIC, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED));
    ourMethodIncompatibleModifiers.put(PsiModifier.PROTECTED, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE));
    ourMethodIncompatibleModifiers.put(PsiModifier.STATIC, Set.of(PsiModifier.ABSTRACT, PsiModifier.DEFAULT));
    ourMethodIncompatibleModifiers
      .put(PsiModifier.DEFAULT, Set.of(PsiModifier.ABSTRACT, PsiModifier.STATIC, PsiModifier.PRIVATE));
    ourMethodIncompatibleModifiers.put(PsiModifier.SYNCHRONIZED, Set.of(PsiModifier.ABSTRACT));
    ourMethodIncompatibleModifiers.put(PsiModifier.STRICTFP, Set.of(PsiModifier.ABSTRACT));
    ourMethodIncompatibleModifiers.put(PsiModifier.FINAL, Set.of(PsiModifier.ABSTRACT));

    ourFieldIncompatibleModifiers.put(PsiModifier.FINAL, Set.of(PsiModifier.VOLATILE));
    ourFieldIncompatibleModifiers.put(PsiModifier.PACKAGE_LOCAL, Set.of(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourFieldIncompatibleModifiers.put(PsiModifier.PRIVATE, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED));
    ourFieldIncompatibleModifiers.put(PsiModifier.PUBLIC, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED));
    ourFieldIncompatibleModifiers.put(PsiModifier.PROTECTED, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE));
    ourFieldIncompatibleModifiers.put(PsiModifier.STATIC, Set.of());
    ourFieldIncompatibleModifiers.put(PsiModifier.TRANSIENT, Set.of());
    ourFieldIncompatibleModifiers.put(PsiModifier.VOLATILE, Set.of(PsiModifier.FINAL));

    ourClassInitializerIncompatibleModifiers.put(PsiModifier.STATIC, Set.of());

    ourModuleIncompatibleModifiers.put(PsiModifier.OPEN, Set.of());

    ourRequiresIncompatibleModifiers.put(PsiModifier.STATIC, Set.of());
    ourRequiresIncompatibleModifiers.put(PsiModifier.TRANSITIVE, Set.of());
  }

  private HighlightUtil() { }

  @NotNull
  private static QuickFixFactory getFixFactory() {
    return QuickFixFactory.getInstance();
  }

  private static String getIncompatibleModifier(@NotNull String modifier,
                                                @NotNull PsiModifierList modifierList,
                                                @NotNull Map<String, Set<String>> incompatibleModifiersHash) {
    // modifier is always incompatible with itself
    PsiElement[] modifiers = modifierList.getChildren();
    int modifierCount = 0;
    for (PsiElement otherModifier : modifiers) {
      if (Comparing.equal(modifier, otherModifier.getText(), true)) modifierCount++;
    }
    if (modifierCount > 1) {
      return modifier;
    }

    Set<String> incompatibles = incompatibleModifiersHash.get(modifier);
    if (incompatibles == null) return null;
    PsiElement parent = modifierList.getParent();
    boolean level8OrHigher = PsiUtil.isLanguageLevel8OrHigher(modifierList);
    boolean level9OrHigher = PsiUtil.isLanguageLevel9OrHigher(modifierList);
    for (@PsiModifier.ModifierConstant String incompatible : incompatibles) {
      if (level8OrHigher) {
        if (modifier.equals(PsiModifier.STATIC) && incompatible.equals(PsiModifier.ABSTRACT)) {
          continue;
        }
      }
      if (parent instanceof PsiMethod) {
        if (level9OrHigher && modifier.equals(PsiModifier.PRIVATE) && incompatible.equals(PsiModifier.PUBLIC)) {
          continue;
        }

        if (modifier.equals(PsiModifier.STATIC) && incompatible.equals(PsiModifier.FINAL)) {
          PsiClass containingClass = ((PsiMethod)parent).getContainingClass();
          if (containingClass == null || !containingClass.isInterface()) {
            continue;
          }
        }
      }
      if (modifierList.hasModifierProperty(incompatible)) {
        return incompatible;
      }
      if (PsiModifier.ABSTRACT.equals(incompatible) && modifierList.hasExplicitModifier(incompatible)) {
        return incompatible;
      }
    }

    return null;
  }


  static void checkInstanceOfApplicable(@NotNull PsiInstanceOfExpression expression, @NotNull HighlightInfoHolder holder) {
    PsiExpression operand = expression.getOperand();
    PsiTypeElement typeElement = InstanceOfUtils.findCheckTypeElement(expression);
    if (typeElement == null) return;
    PsiType checkType = typeElement.getType();
    PsiType operandType = operand.getType();
    if (operandType == null) return;
    if (TypeConversionUtil.isPrimitiveAndNotNull(operandType)
        || TypeConversionUtil.isPrimitiveAndNotNull(checkType)
        || !TypeConversionUtil.areTypesConvertible(operandType, checkType)) {
      String message = JavaErrorBundle.message("inconvertible.type.cast", JavaHighlightUtil.formatType(operandType), JavaHighlightUtil
        .formatType(checkType));
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
      if (TypeConversionUtil.isPrimitiveAndNotNull(checkType)) {
        IntentionAction action = getFixFactory().createReplacePrimitiveWithBoxedTypeAction(operandType, typeElement);
        if (action != null) {
          info.registerFix(action, null, null, null, null);
        }
      }
      holder.add(info.create());
      return;
    }
    PsiPrimaryPattern pattern = expression.getPattern();
    if (pattern instanceof PsiDeconstructionPattern deconstruction) {
      PatternHighlightingModel.createDeconstructionErrors(deconstruction, holder);
    }
  }


  /**
   * 15.16 Cast Expressions
   * ( ReferenceType {AdditionalBound} ) expression, where AdditionalBound: & InterfaceType then all must be true
   * - ReferenceType must denote a class or interface type.
   * - The erasures of all the listed types must be pairwise different.
   * - No two listed types may be subtypes of different parameterization of the same generic interface.
   */
  static HighlightInfo.Builder checkIntersectionInTypeCast(@NotNull PsiTypeCastExpression expression,
                                                   @NotNull LanguageLevel languageLevel,
                                                   @NotNull PsiFile file) {
    PsiTypeElement castTypeElement = expression.getCastType();
    if (castTypeElement == null || !isIntersection(castTypeElement, castTypeElement.getType())) {
      return null;
    }
    HighlightInfo.Builder info = checkFeature(expression, HighlightingFeature.INTERSECTION_CASTS, languageLevel, file);
    if (info != null) return info;

    PsiTypeElement[] conjuncts = PsiTreeUtil.getChildrenOfType(castTypeElement, PsiTypeElement.class);
    if (conjuncts != null) {
      Set<PsiType> erasures = new HashSet<>(conjuncts.length);
      erasures.add(TypeConversionUtil.erasure(conjuncts[0].getType()));
      List<PsiTypeElement> conjList = new ArrayList<>(Arrays.asList(conjuncts));
      for (int i = 1; i < conjuncts.length; i++) {
        PsiTypeElement conjunct = conjuncts[i];
        PsiType conjType = conjunct.getType();
        if (conjType instanceof PsiClassType) {
          PsiClass aClass = ((PsiClassType)conjType).resolve();
          if (aClass != null && !aClass.isInterface()) {
            HighlightInfo.Builder errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
              .range(conjunct)
              .descriptionAndTooltip(JavaErrorBundle.message("interface.expected"));
            IntentionAction action = new FlipIntersectionSidesFix(aClass.getName(), conjunct, castTypeElement);
            errorResult.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(null), null, null);
            return errorResult;
          }
        }
        else {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(conjunct)
            .descriptionAndTooltip(JavaErrorBundle.message("unexpected.type.class.expected"));
        }
        if (!erasures.add(TypeConversionUtil.erasure(conjType))) {
          HighlightInfo.Builder highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(conjunct)
            .descriptionAndTooltip(JavaErrorBundle.message("repeated.interface"));
          IntentionAction action = new DeleteRepeatedInterfaceFix(conjunct);
          highlightInfo.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(null), null, null);
          return highlightInfo;
        }
      }

      List<PsiType> typeList = ContainerUtil.map(conjList, PsiTypeElement::getType);
      Ref<@Nls String> differentArgumentsMessage = new Ref<>();
      PsiClass sameGenericParameterization =
        InferenceSession.findParameterizationOfTheSameGenericClass(typeList, pair -> {
          if (!TypesDistinctProver.provablyDistinct(pair.first, pair.second)) {
            return true;
          }
          differentArgumentsMessage.set(IdeBundle.message("x.and.y", pair.first.getPresentableText(),
                                                          pair.second.getPresentableText()));
          return false;
        });
      if (sameGenericParameterization != null) {
        String message = JavaErrorBundle
          .message("class.cannot.be.inherited.with.different.arguments", formatClass(sameGenericParameterization),
                   differentArgumentsMessage.get());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(expression)
          .descriptionAndTooltip(message);
      }
    }

    return null;
  }

  private static boolean isIntersection(@NotNull PsiTypeElement castTypeElement, @NotNull PsiType castType) {
    if (castType instanceof PsiIntersectionType) return true;
    return castType instanceof PsiClassType && PsiTreeUtil.getChildrenOfType(castTypeElement, PsiTypeElement.class) != null;
  }

  static HighlightInfo.Builder checkInconvertibleTypeCast(@NotNull PsiTypeCastExpression expression) {
    PsiTypeElement castTypeElement = expression.getCastType();
    if (castTypeElement == null) return null;
    PsiType castType = castTypeElement.getType();

    PsiExpression operand = expression.getOperand();
    if (operand == null) return null;
    PsiType operandType = operand.getType();

    if (operandType != null &&
        !TypeConversionUtil.areTypesConvertible(operandType, castType, PsiUtil.getLanguageLevel(expression)) &&
        !RedundantCastUtil.isInPolymorphicCall(expression)) {
      String message = JavaErrorBundle.message("inconvertible.type.cast", JavaHighlightUtil.formatType(operandType), JavaHighlightUtil
        .formatType(castType));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
    }
    return null;
  }

  static HighlightInfo.Builder checkVariableExpected(@NotNull PsiExpression expression) {
    PsiExpression lValue;
    if (expression instanceof PsiAssignmentExpression assignment) {
      lValue = assignment.getLExpression();
    }
    else if (PsiUtil.isIncrementDecrementOperation(expression)) {
      lValue = ((PsiUnaryExpression)expression).getOperand();
    }
    else {
      lValue = null;
    }
    HighlightInfo.Builder errorResult = null;
    if (lValue != null && !TypeConversionUtil.isLValue(lValue)) {
      String description = JavaErrorBundle.message("variable.expected");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(lValue).descriptionAndTooltip(description);
    }

    return errorResult;
  }


  static HighlightInfo.Builder checkAssignmentOperatorApplicable(@NotNull PsiAssignmentExpression assignment) {
    PsiJavaToken operationSign = assignment.getOperationSign();
    IElementType eqOpSign = operationSign.getTokenType();
    IElementType opSign = TypeConversionUtil.convertEQtoOperation(eqOpSign);
    if (opSign == null) return null;
    PsiType lType = assignment.getLExpression().getType();
    PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return null;
    PsiType rType = rExpression.getType();
    HighlightInfo.Builder errorResult = null;
    if (!TypeConversionUtil.isBinaryOperatorApplicable(opSign, lType, rType, true)) {
      String operatorText = operationSign.getText().substring(0, operationSign.getText().length() - 1);
      String message = JavaErrorBundle.message("binary.operator.not.applicable", operatorText,
                                               JavaHighlightUtil.formatType(lType),
                                               JavaHighlightUtil.formatType(rType));

      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(operationSign).descriptionAndTooltip(message);
    }
    return errorResult;
  }


  static HighlightInfo.Builder checkAssignmentCompatibleTypes(@NotNull PsiAssignmentExpression assignment) {
    PsiExpression lExpr = assignment.getLExpression();
    PsiExpression rExpr = assignment.getRExpression();
    if (rExpr == null) return null;
    PsiType lType = lExpr.getType();
    PsiType rType = rExpr.getType();
    if (rType == null) return null;

    IElementType sign = assignment.getOperationTokenType();
    HighlightInfo.Builder highlightInfo;
    if (JavaTokenType.EQ.equals(sign)) {
      highlightInfo = checkAssignability(lType, rType, rExpr, assignment);
    }
    else {
      // 15.26.2. Compound Assignment Operators
      IElementType opSign = TypeConversionUtil.convertEQtoOperation(sign);
      PsiType type = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, opSign, true);
      if (type == null || lType == null || TypeConversionUtil.areTypesConvertible(type, lType)) {
        return null;
      }
      highlightInfo = createIncompatibleTypeHighlightInfo(lType, type, assignment.getTextRange(), 0);
      IntentionAction action = getFixFactory().createChangeToAppendFix(sign, lType, assignment);
      highlightInfo.registerFix(action, null, null, null, null);
    }
    if (highlightInfo == null) {
      return null;
    }
    HighlightFixUtil.registerChangeVariableTypeFixes(lExpr, rType, rExpr, highlightInfo);
    if (lType != null) {
      HighlightFixUtil.registerChangeVariableTypeFixes(rExpr, lType, lExpr, highlightInfo);
    }
    return highlightInfo;
  }

  private static boolean isCastIntentionApplicable(@NotNull PsiExpression expression, @Nullable PsiType toType) {
    while (expression instanceof PsiTypeCastExpression || expression instanceof PsiParenthesizedExpression) {
      if (expression instanceof PsiTypeCastExpression) {
        expression = ((PsiTypeCastExpression)expression).getOperand();
      }
      if (expression instanceof PsiParenthesizedExpression) {
        expression = ((PsiParenthesizedExpression)expression).getExpression();
      }
    }
    if (expression == null) return false;
    PsiType rType = expression.getType();
    PsiType castType = GenericsUtil.getVariableTypeByExpressionType(toType);
    return rType != null && toType != null && TypeConversionUtil.areTypesConvertible(rType, toType) && toType.isAssignableFrom(castType);
  }


  static HighlightInfo.Builder checkVariableInitializerType(@NotNull PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    // array initializer checked in checkArrayInitializerApplicable
    if (initializer == null || initializer instanceof PsiArrayInitializerExpression) return null;
    PsiType lType = variable.getType();
    PsiType rType = initializer.getType();
    PsiTypeElement typeElement = variable.getTypeElement();
    int start = typeElement != null ? typeElement.getTextRange().getStartOffset() : variable.getTextRange().getStartOffset();
    int end = variable.getTextRange().getEndOffset();
    HighlightInfo.Builder highlightInfo = checkAssignability(lType, rType, initializer, new TextRange(start, end), 0);
    if (highlightInfo != null) {
      HighlightFixUtil.registerChangeVariableTypeFixes(variable, rType, variable.getInitializer(), highlightInfo);
      HighlightFixUtil.registerChangeVariableTypeFixes(initializer, lType, null, highlightInfo);
    }
    return highlightInfo;
  }

  static HighlightInfo.Builder checkRestrictedIdentifierReference(@NotNull PsiJavaCodeReferenceElement ref,
                                                          @NotNull PsiClass resolved,
                                                          @NotNull LanguageLevel languageLevel) {
    String name = resolved.getName();
    if (HighlightClassUtil.isRestrictedIdentifier(name, languageLevel)) {
      String message = JavaErrorBundle.message("restricted.identifier.reference", name);
      PsiElement range = ObjectUtils.notNull(ref.getReferenceNameElement(), ref);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(message).range(range);
    }
    return null;
  }

  static HighlightInfo.Builder checkVarTypeSelfReferencing(@NotNull PsiLocalVariable resolved, @NotNull PsiReferenceExpression ref) {
    if (PsiTreeUtil.isAncestor(resolved.getInitializer(), ref, false) && resolved.getTypeElement().isInferredType()) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .descriptionAndTooltip(JavaErrorBundle.message("lvti.selfReferenced", resolved.getName()))
        .range(ref);
    }
    return null;
  }
  
  static HighlightInfo.Builder checkVarTypeApplicability(@NotNull PsiVariable variable) {
    if (variable instanceof PsiLocalVariable && variable.getTypeElement().isInferredType()) {
      PsiElement parent = variable.getParent();
      if (parent instanceof PsiDeclarationStatement && ((PsiDeclarationStatement)parent).getDeclaredElements().length > 1) {
        String message = JavaErrorBundle.message("lvti.compound");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(message).range(variable);
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkVarTypeApplicability(@NotNull PsiTypeElement typeElement) {
    if (!typeElement.isInferredType()) {
      return null;
    }
    PsiElement parent = typeElement.getParent();
    PsiVariable variable = tryCast(parent, PsiVariable.class);
    if (variable instanceof PsiLocalVariable) {
      PsiExpression initializer = variable.getInitializer();
      if (initializer == null) {
        String message = JavaErrorBundle.message("lvti.no.initializer");
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(message).range(typeElement);
        HighlightFixUtil.registerSpecifyVarTypeFix((PsiLocalVariable)variable, info);
        return info;
      }
      if (initializer instanceof PsiFunctionalExpression) {
        boolean lambda = initializer instanceof PsiLambdaExpression;
        String message = JavaErrorBundle.message(lambda ? "lvti.lambda" : "lvti.method.ref");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(message).range(typeElement);
      }

      if (isArrayDeclaration(variable)) {
        String message = JavaErrorBundle.message("lvti.array");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(message).range(typeElement);
      }

      PsiType lType = variable.getType();
      if (PsiTypes.nullType().equals(lType) && SyntaxTraverser.psiTraverser(initializer)
                                          .filter(PsiLiteralExpression.class)
                                          .find(l -> PsiTypes.nullType().equals(l.getType())) != null) {
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(JavaErrorBundle.message("lvti.null"))
            .range(typeElement);
        HighlightFixUtil.registerSpecifyVarTypeFix((PsiLocalVariable)variable, info);
        return info;
      }
      if (PsiTypes.voidType().equals(lType)) {
        String message = JavaErrorBundle.message("lvti.void");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(message).range(typeElement);
      }
    }
    else if (variable instanceof PsiParameter && variable.getParent() instanceof PsiParameterList && isArrayDeclaration(variable)) {
      String message = JavaErrorBundle.message("lvti.array");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(message).range(typeElement);
    }

    return null;
  }

  private static boolean isArrayDeclaration(@NotNull PsiVariable variable) {
    // Java-style 'var' arrays are prohibited by the parser; for C-style ones, looking for a bracket is enough
    return ContainerUtil.or(variable.getChildren(), e -> PsiUtil.isJavaToken(e, JavaTokenType.LBRACKET));
  }

  static HighlightInfo.Builder checkAssignability(@Nullable PsiType lType,
                                          @Nullable PsiType rType,
                                          @Nullable PsiExpression expression,
                                          @NotNull PsiElement elementToHighlight) {
    TextRange textRange = elementToHighlight.getTextRange();
    return checkAssignability(lType, rType, expression, textRange, 0);
  }

  private static HighlightInfo.Builder checkAssignability(@Nullable PsiType lType,
                                                  @Nullable PsiType rType,
                                                  @Nullable PsiExpression expression,
                                                  @NotNull TextRange textRange,
                                                  int navigationShift) {
    if (lType == rType) return null;
    if (expression == null) {
      if (rType == null || lType == null || TypeConversionUtil.isAssignable(lType, rType)) return null;
    }
    else if (TypeConversionUtil.areTypesAssignmentCompatible(lType, expression)) {
      return null;
    }
    if (rType == null) {
      rType = expression.getType();
    }
    if (lType == null || lType == PsiTypes.nullType()) {
      return null;
    }
    HighlightInfo.Builder highlightInfo = createIncompatibleTypeHighlightInfo(lType, rType, textRange, navigationShift);
    AddTypeArgumentsConditionalFix.register(highlightInfo, expression, lType);
    if (rType != null && expression != null && isCastIntentionApplicable(expression, lType)) {
      IntentionAction action = getFixFactory().createAddTypeCastFix(lType, expression);
      highlightInfo.registerFix(action, null, null, null, null);
    }
    if (expression != null) {
      AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(highlightInfo, textRange, expression, lType, rType);
      if (!(expression.getParent() instanceof PsiConditionalExpression && PsiTypes.voidType().equals(lType))) {
        HighlightFixUtil.registerChangeReturnTypeFix(highlightInfo, expression, lType);
      }
    }
    ChangeNewOperatorTypeFix.register(highlightInfo, expression, lType);
    return highlightInfo;
  }

  static HighlightInfo.Builder checkReturnFromSwitchExpr(@NotNull PsiReturnStatement statement) {
    if (PsiImplUtil.findEnclosingSwitchExpression(statement) != null) {
      String message = JavaErrorBundle.message("return.outside.switch.expr");
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message);
      if (statement.getReturnValue() != null) {
        var action = new ReplaceWithYieldFix(statement);
        info.registerFix(action, null, null, null, null);
      }
      return info;
    }

    return null;
  }

  static HighlightInfo.Builder checkReturnStatementType(@NotNull PsiReturnStatement statement, @NotNull PsiElement parent) {
    if (parent instanceof PsiCodeFragment || parent instanceof PsiLambdaExpression) {
      return null;
    }
    PsiMethod method = tryCast(parent, PsiMethod.class);
    String description;
    HighlightInfo.Builder errorResult = null;
    if (method == null && !(parent instanceof ServerPageFile)) {
      description = JavaErrorBundle.message("return.outside.method");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description);
    }
    else {
      PsiType returnType = method != null ? method.getReturnType() : null/*JSP page returns void*/;
      boolean isMethodVoid = returnType == null || PsiTypes.voidType().equals(returnType);
      PsiExpression returnValue = statement.getReturnValue();
      if (returnValue != null) {
        PsiType valueType = RefactoringChangeUtil.getTypeByExpression(returnValue);
        if (isMethodVoid) {
          description = JavaErrorBundle.message("return.from.void.method");
          errorResult =
            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description);
          if (method != null && valueType != null && method.getBody() != null) {
            IntentionAction action1 = getFixFactory().createDeleteReturnFix(method, statement);
            errorResult.registerFix(action1, null, null, null, null);
            IntentionAction action = getFixFactory().createMethodReturnFix(method, valueType, true);
            errorResult.registerFix(action, null, null, null, null);
          }
        }
        else {
          TextRange textRange = statement.getTextRange();
          errorResult = checkAssignability(returnType, valueType, returnValue, textRange, returnValue.getStartOffsetInParent());
          if (errorResult != null && valueType != null) {
            if (!PsiTypes.voidType().equals(valueType)) {
              IntentionAction action = getFixFactory().createMethodReturnFix(method, valueType, true);
              errorResult.registerFix(action, null, null, null, null);
            }
            HighlightFixUtil.registerChangeParameterClassFix(returnType, valueType, errorResult);
          }
        }
      }
      else if (!isMethodVoid) {
        description = JavaErrorBundle.message("missing.return.value");
        errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description)
          .navigationShift(PsiKeyword.RETURN.length());
        IntentionAction action = getFixFactory().createMethodReturnFix(method, PsiTypes.voidType(), true);
        errorResult.registerFix(action, null, null, null, null);
      }
    }
    return errorResult;
  }

  static void registerReturnTypeFixes(@NotNull HighlightInfo.Builder info, @NotNull PsiMethod method, @NotNull PsiType expectedReturnType) {
    IntentionAction action = getFixFactory().createMethodReturnFix(method, expectedReturnType, true, true);
    info.registerFix(action, null, null, null, null);
  }

  @NotNull
  public static @NlsContexts.DetailedDescription String getUnhandledExceptionsDescriptor(@NotNull Collection<? extends PsiClassType> unhandled) {
    return JavaErrorBundle.message("unhandled.exceptions", formatTypes(unhandled), unhandled.size());
  }

  @NotNull
  private static String formatTypes(@NotNull Collection<? extends PsiClassType> unhandled) {
    return StringUtil.join(unhandled, JavaHighlightUtil::formatType, ", ");
  }

  public static HighlightInfo.Builder checkVariableAlreadyDefined(@NotNull PsiVariable variable) {
    if (variable instanceof ExternallyDefinedPsiElement) return null;
    PsiVariable oldVariable = null;
    PsiElement declarationScope = null;
    if (variable instanceof PsiLocalVariable || variable instanceof PsiPatternVariable ||
        variable instanceof PsiParameter &&
        ((declarationScope = ((PsiParameter)variable).getDeclarationScope()) instanceof PsiCatchSection ||
         declarationScope instanceof PsiForeachStatement ||
         declarationScope instanceof PsiLambdaExpression)) {
      PsiElement scope =
        PsiTreeUtil.getParentOfType(variable, PsiFile.class, PsiMethod.class, PsiClassInitializer.class, PsiResourceList.class);
      VariablesNotProcessor proc = new VariablesNotProcessor(variable, false) {
        @Override
        protected boolean check(PsiVariable var, ResolveState state) {
          return PsiUtil.isJvmLocalVariable(var) && super.check(var, state);
        }
      };
      PsiIdentifier identifier = variable.getNameIdentifier();
      assert identifier != null : variable;
      PsiScopesUtil.treeWalkUp(proc, identifier, scope);
      if (scope instanceof PsiResourceList && proc.size() == 0) {
        scope = PsiTreeUtil.getParentOfType(variable, PsiFile.class, PsiMethod.class, PsiClassInitializer.class);
        PsiScopesUtil.treeWalkUp(proc, identifier, scope);
      }
      if (proc.size() > 0) {
        oldVariable = proc.getResult(0);
      }
      else if (declarationScope instanceof PsiLambdaExpression) {
        oldVariable = findSameNameSibling(variable);
      }
      else if (variable instanceof PsiPatternVariable) {
        oldVariable = findSamePatternVariableInBranches((PsiPatternVariable)variable);
      }
    }
    else if (variable instanceof PsiField field) {
      PsiClass aClass = field.getContainingClass();
      if (aClass == null) return null;
      PsiField fieldByName = aClass.findFieldByName(variable.getName(), false);
      if (fieldByName != null && fieldByName != field) {
        oldVariable = fieldByName;
      }
      else {
        oldVariable = ContainerUtil.find(aClass.getRecordComponents(), c -> field.getName().equals(c.getName()));
      }
    }
    else {
      oldVariable = findSameNameSibling(variable);
    }

    if (oldVariable != null) {
      String description = JavaErrorBundle.message("variable.already.defined", variable.getName());
      PsiIdentifier identifier = variable.getNameIdentifier();
      assert identifier != null : variable;
      VirtualFile vFile = PsiUtilCore.getVirtualFile(identifier);
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier);
      if (vFile != null) {
        String path = FileUtil.toSystemIndependentName(vFile.getPath());
        String linkText = "<a href=\"#navigation/" + path + ":" + oldVariable.getTextOffset() + "\">" + variable.getName() + "</a>";
        builder = builder.description(description)
          .escapedToolTip("<html>" + JavaErrorBundle.message("variable.already.defined", linkText) + "</html>");
      }
      else {
        builder = builder.descriptionAndTooltip(description);
      }
      IntentionAction action1 = getFixFactory().createNavigateToAlreadyDeclaredVariableFix(oldVariable);
      builder.registerFix(action1, null, null, null, null);
      if (variable instanceof PsiLocalVariable) {
        IntentionAction action = getFixFactory().createReuseVariableDeclarationFix((PsiLocalVariable)variable);
        builder.registerFix(action, null, null, null, null);
      }
      return builder;
    }
    return null;
  }

  private static PsiPatternVariable findSamePatternVariableInBranches(@NotNull PsiPatternVariable variable) {
    PsiPattern pattern = variable.getPattern();
    PatternResolveState hint = PatternResolveState.WHEN_TRUE;
    VariablesNotProcessor proc = new VariablesNotProcessor(variable, false) {
      @Override
      protected boolean check(PsiVariable var, ResolveState state) {
        return var instanceof PsiPatternVariable && super.check(var, state);
      }
    };
    PsiElement lastParent = pattern;
    for (PsiElement parent = lastParent.getParent(); parent != null; lastParent = parent, parent = parent.getParent()) {
      if (parent instanceof PsiInstanceOfExpression || parent instanceof PsiParenthesizedExpression) continue;
      if (parent instanceof PsiPrefixExpression && ((PsiPrefixExpression)parent).getOperationTokenType().equals(JavaTokenType.EXCL)) {
        hint = hint.invert();
        continue;
      }
      if (parent instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression)parent).getOperationTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
          PatternResolveState targetHint = PatternResolveState.fromBoolean(tokenType.equals(JavaTokenType.OROR));
          if (hint == targetHint) {
            for (PsiExpression operand : ((PsiPolyadicExpression)parent).getOperands()) {
              if (operand == lastParent) break;
              operand.processDeclarations(proc, hint.putInto(ResolveState.initial()), null, pattern);
            }
          }
          continue;
        }
      }
      if (parent instanceof PsiConditionalExpression conditional) {
        PsiExpression thenExpression = conditional.getThenExpression();
        if (lastParent == thenExpression) {
          conditional.getCondition()
            .processDeclarations(proc, PatternResolveState.WHEN_FALSE.putInto(ResolveState.initial()), null, pattern);
        }
        else if (lastParent == conditional.getElseExpression()) {
          conditional.getCondition()
            .processDeclarations(proc, PatternResolveState.WHEN_TRUE.putInto(ResolveState.initial()), null, pattern);
          if (thenExpression != null) {
            thenExpression.processDeclarations(proc, hint.putInto(ResolveState.initial()), null, pattern);
          }
        }
      }
      break;
    }
    return proc.size() > 0 ? (PsiPatternVariable)proc.getResult(0) : null;
  }

  private static PsiVariable findSameNameSibling(@NotNull PsiVariable variable) {
    PsiElement scope = variable.getParent();
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiVariable) {
        if (child.equals(variable)) continue;
        if (Objects.equals(variable.getName(), ((PsiVariable)child).getName())) {
          return (PsiVariable)child;
        }
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkUnderscore(@NotNull PsiIdentifier identifier, @NotNull LanguageLevel languageLevel) {
    if ("_".equals(identifier.getText())) {
      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
        String text = JavaErrorBundle.message("underscore.identifier.error");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(text);
      }
      else if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        PsiElement parent = identifier.getParent();
        if (parent instanceof PsiParameter && ((PsiParameter)parent).getDeclarationScope() instanceof PsiLambdaExpression) {
          String text = JavaErrorBundle.message("underscore.lambda.identifier");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(text);
        }
      }
    }

    return null;
  }

  @NotNull
  public static @NlsSafe String formatClass(@NotNull PsiClass aClass) {
    return formatClass(aClass, true);
  }

  @NotNull
  public static String formatClass(@NotNull PsiClass aClass, boolean fqn) {
    return PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_ANONYMOUS_CLASS_VERBOSE | (fqn ? PsiFormatUtilBase.SHOW_FQ_NAME : 0));
  }

  @NotNull
  private static String formatField(@NotNull PsiField field) {
    return PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
  }

  static HighlightInfo.Builder checkUnhandledExceptions(@NotNull PsiElement element) {
    List<PsiClassType> unhandled = ExceptionUtil.getOwnUnhandledExceptions(element);
    if (unhandled.isEmpty()) return null;

    HighlightInfoType highlightType = getUnhandledExceptionHighlightType(element);
    if (highlightType == null) return null;

    TextRange textRange = computeRange(element);
    String description = getUnhandledExceptionsDescriptor(unhandled);
    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(highlightType).range(textRange).descriptionAndTooltip(description);
    HighlightFixUtil.registerUnhandledExceptionFixes(element, info);
    return info;
  }

  private static TextRange computeRange(@NotNull PsiElement element) {
    if (element instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement reference = ((PsiNewExpression)element).getClassReference();
      if (reference != null) {
        return reference.getTextRange();
      }
    }
    if (element instanceof PsiEnumConstant) {
      return ((PsiEnumConstant)element).getNameIdentifier().getTextRange();
    }
    if (element instanceof PsiMethodCallExpression) {
      PsiElement nameElement = ((PsiMethodCallExpression)element).getMethodExpression().getReferenceNameElement();
      if (nameElement != null) {
        return nameElement.getTextRange();
      }
    }
    return HighlightMethodUtil.getFixRange(element);
  }

  static HighlightInfo.Builder checkUnhandledCloserExceptions(@NotNull PsiResourceListElement resource) {
    List<PsiClassType> unhandled = ExceptionUtil.getUnhandledCloserExceptions(resource, null);
    if (unhandled.isEmpty()) return null;

    HighlightInfoType highlightType = getUnhandledExceptionHighlightType(resource);
    if (highlightType == null) return null;

    String description = JavaErrorBundle.message("unhandled.close.exceptions", formatTypes(unhandled), unhandled.size(),
                              JavaErrorBundle.message("auto.closeable.resource"));
    HighlightInfo.Builder highlight = HighlightInfo.newHighlightInfo(highlightType).range(resource).descriptionAndTooltip(description);
    HighlightFixUtil.registerUnhandledExceptionFixes(resource, highlight);
    return highlight;
  }

  @Nullable
  private static HighlightInfoType getUnhandledExceptionHighlightType(@NotNull PsiElement element) {
    // JSP top-level errors are handled by UnhandledExceptionInJSP inspection
    if (FileTypeUtils.isInServerPageFile(element)) {
      PsiMethod targetMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, true, PsiLambdaExpression.class);
      if (targetMethod instanceof SyntheticElement) {
        return null;
      }
    }

    return HighlightInfoType.UNHANDLED_EXCEPTION;
  }

  static HighlightInfo.Builder checkBreakTarget(@NotNull PsiBreakStatement statement, @NotNull LanguageLevel languageLevel) {
    return checkBreakOrContinueTarget(statement, statement.getLabelIdentifier(), statement.findExitedStatement(), languageLevel,
                                      "break.outside.switch.or.loop",
                                      "break.outside.switch.expr");
  }

  static HighlightInfo.Builder checkYieldOutsideSwitchExpression(@NotNull PsiYieldStatement statement) {
    if (statement.findEnclosingExpression() == null) {
      String message = JavaErrorBundle.message("yield.unexpected");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message);
    }
    return null;
  }

  static HighlightInfo.Builder checkYieldExpressionType(@NotNull PsiExpression expression) {
    if (PsiTypes.voidType().equals(expression.getType())) {
      String message = JavaErrorBundle.message("yield.void");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
    }

    return null;
  }

  static HighlightInfo.Builder checkContinueTarget(@NotNull PsiContinueStatement statement, @NotNull LanguageLevel languageLevel) {
    PsiStatement continuedStatement = statement.findContinuedStatement();
    PsiIdentifier label = statement.getLabelIdentifier();

    if (label != null && continuedStatement != null && !(continuedStatement instanceof PsiLoopStatement)) {
      String message = JavaErrorBundle.message("not.loop.label", label.getText());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message);
    }

    return checkBreakOrContinueTarget(statement, label, continuedStatement, languageLevel,
                                      "continue.outside.loop",
                                      "continue.outside.switch.expr");
  }

  private static HighlightInfo.Builder checkBreakOrContinueTarget(@NotNull PsiStatement statement,
                                                          @Nullable PsiIdentifier label,
                                                          @Nullable PsiStatement target,
                                                          @NotNull LanguageLevel level,
                                                          @NotNull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String misplacedKey,
                                                          @NotNull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String crossingKey) {
    if (target == null && label != null) {
      String message = JavaErrorBundle.message("unresolved.label", label.getText());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(label).descriptionAndTooltip(message);
    }

    if (HighlightingFeature.ENHANCED_SWITCH.isSufficient(level)) {
      PsiSwitchExpression expression = PsiImplUtil.findEnclosingSwitchExpression(statement);
      if (expression != null && (target == null || PsiTreeUtil.isAncestor(target, expression, true))) {
        String message = JavaErrorBundle.message(crossingKey);
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message);
      }
    }

    if (target == null) {
      String message = JavaErrorBundle.message(misplacedKey);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message);
    }

    return null;
  }

  static HighlightInfo.Builder checkIllegalModifierCombination(@NotNull PsiKeyword keyword, @NotNull PsiModifierList modifierList) {
    @PsiModifier.ModifierConstant String modifier = keyword.getText();
    String incompatible = getIncompatibleModifier(modifier, modifierList);
    if (incompatible != null) {
      String message = JavaErrorBundle.message("incompatible.modifiers", modifier, incompatible);
      HighlightInfo.Builder highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(message);
      IntentionAction action = getFixFactory().createModifierListFix(modifierList, modifier, false, false);
      highlightInfo.registerFix(action, null, null, null, null);
      return highlightInfo;
    }

    return null;
  }

  /**
   * Checks if the supplied modifier list contains incompatible modifiers (e.g. "public private").
   *
   * @param modifierList a {@link PsiModifierList} to check
   * @return true if the supplied modifier list contains compatible modifiers
   */
  public static boolean isLegalModifierCombination(@NotNull PsiModifierList modifierList) {
    for (PsiElement child = modifierList.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiKeyword && getIncompatibleModifier(child.getText(), modifierList) != null) {
        return false;
      }
    }
    return true;
  }

  private static Map<String, Set<String>> getIncompatibleModifierMap(@NotNull PsiElement modifierListOwner) {
    if (PsiUtilCore.hasErrorElementChild(modifierListOwner)) return null;
    if (modifierListOwner instanceof PsiClass) {
      return ((PsiClass)modifierListOwner).isInterface() ? ourInterfaceIncompatibleModifiers : ourClassIncompatibleModifiers;
    }
    if (modifierListOwner instanceof PsiMethod) return ourMethodIncompatibleModifiers;
    if (modifierListOwner instanceof PsiVariable) return ourFieldIncompatibleModifiers;
    if (modifierListOwner instanceof PsiClassInitializer) return ourClassInitializerIncompatibleModifiers;
    if (modifierListOwner instanceof PsiJavaModule) return ourModuleIncompatibleModifiers;
    if (modifierListOwner instanceof PsiRequiresStatement) return ourRequiresIncompatibleModifiers;
    return null;
  }

  @Nullable
  static String getIncompatibleModifier(@NotNull String modifier, @NotNull PsiModifierList modifierList) {
    PsiElement parent = modifierList.getParent();
    if (parent == null) return null;
    Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(parent);
    return incompatibleModifierMap == null ? null : getIncompatibleModifier(modifier, modifierList, incompatibleModifierMap);
  }

  static HighlightInfo.Builder checkNotAllowedModifier(@NotNull PsiKeyword keyword, @NotNull PsiModifierList modifierList) {
    PsiElement modifierOwner = modifierList.getParent();
    if (modifierOwner == null) return null;
    Map<String, Set<String>> incompatibleModifierMap = getIncompatibleModifierMap(modifierOwner);
    if (incompatibleModifierMap == null) return null;

    @PsiModifier.ModifierConstant String modifier = keyword.getText();
    Set<String> incompatibles = incompatibleModifierMap.get(modifier);
    PsiElement modifierOwnerParent =
      modifierOwner instanceof PsiMember ? ((PsiMember)modifierOwner).getContainingClass() : modifierOwner.getParent();
    if (modifierOwnerParent == null) modifierOwnerParent = modifierOwner.getParent();
    boolean isAllowed = true;
    IntentionAction fix = null;
    if (modifierOwner instanceof PsiClass aClass) {
      boolean privateOrProtected = PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier);
      if (aClass.isInterface()) {
        if (PsiModifier.STATIC.equals(modifier) || privateOrProtected || PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiClass;
        }
        if (PsiModifier.PUBLIC.equals(modifier)) {
          isAllowed = !(modifierOwnerParent instanceof PsiDeclarationStatement);
        }
        if (PsiModifier.SEALED.equals(modifier)) {
          isAllowed = !aClass.isAnnotationType();
        }
      }
      else {
        if (PsiModifier.PUBLIC.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiImportHolder ||
                      // PsiJavaFile or JavaDummyHolder
                      modifierOwnerParent instanceof PsiClass &&
                      (modifierOwnerParent instanceof PsiSyntheticClass ||
                       PsiUtil.isLanguageLevel16OrHigher(modifierOwnerParent) ||
                       ((PsiClass)modifierOwnerParent).getQualifiedName() != null ||
                       !modifierOwnerParent.isPhysical());
        }
        else {
          if (PsiModifier.STATIC.equals(modifier) || privateOrProtected || PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
            isAllowed = modifierOwnerParent instanceof PsiClass &&
                        (PsiModifier.STATIC.equals(modifier) || 
                         PsiUtil.isLanguageLevel16OrHigher(modifierOwnerParent) || 
                         ((PsiClass)modifierOwnerParent).getQualifiedName() != null) ||
                        FileTypeUtils.isInServerPageFile(modifierOwnerParent) ||
                        // non-physical dummy holder might not have FQN
                        !modifierOwnerParent.isPhysical();
          }
          if (privateOrProtected && !isAllowed) {
            fix = getFixFactory().createChangeModifierFix();
          }
        }

        if (aClass.isEnum()) {
          isAllowed &=
            !(PsiModifier.FINAL.equals(modifier) || PsiModifier.ABSTRACT.equals(modifier) || PsiModifier.SEALED.equals(modifier));
        }

        if (aClass.isRecord()) {
          isAllowed &= !PsiModifier.ABSTRACT.equals(modifier);
        }

        if (aClass.getContainingClass() instanceof PsiAnonymousClass && 
            privateOrProtected && !PsiUtil.isLanguageLevel16OrHigher(modifierOwnerParent)) {
          isAllowed = false;
        }
      }
      if (PsiModifier.NON_SEALED.equals(modifier) && !aClass.hasModifierProperty(PsiModifier.SEALED)) {
        isAllowed = Arrays.stream(aClass.getSuperTypes())
          .map(PsiClassType::resolve)
          .anyMatch(superClass -> superClass != null && superClass.hasModifierProperty(PsiModifier.SEALED));
      }
    }
    else if (modifierOwner instanceof PsiMethod method) {
      isAllowed = !(method.isConstructor() && ourConstructorNotAllowedModifiers.contains(modifier));
      PsiClass containingClass = method.getContainingClass();
      if ((method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED)) && method.isConstructor() &&
          containingClass != null && containingClass.isEnum()) {
        isAllowed = false;
      }

      boolean isInterface = modifierOwnerParent instanceof PsiClass && ((PsiClass)modifierOwnerParent).isInterface();
      if (PsiModifier.PRIVATE.equals(modifier) && modifierOwnerParent instanceof PsiClass) {
        isAllowed &= !isInterface || PsiUtil.isLanguageLevel9OrHigher(modifierOwner) && !((PsiClass)modifierOwnerParent).isAnnotationType();
      }
      else if (PsiModifier.STRICTFP.equals(modifier)) {
        isAllowed &= !isInterface || PsiUtil.isLanguageLevel8OrHigher(modifierOwner);
      }
      else if (PsiModifier.PROTECTED.equals(modifier) ||
               PsiModifier.TRANSIENT.equals(modifier) ||
               PsiModifier.SYNCHRONIZED.equals(modifier) ||
               PsiModifier.FINAL.equals(modifier)) {
        isAllowed &= !isInterface;
      }

      if (containingClass != null && (containingClass.isInterface() || containingClass.isRecord())) {
        isAllowed &= !PsiModifier.NATIVE.equals(modifier);
      }

      if (containingClass != null && containingClass.isAnnotationType()) {
        isAllowed &= !PsiModifier.STATIC.equals(modifier);
        isAllowed &= !PsiModifier.DEFAULT.equals(modifier);
      }

      if (JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
        isAllowed &= !PsiModifier.STATIC.equals(modifier);
      }
    }
    else if (modifierOwner instanceof PsiField) {
      if (PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) || PsiModifier.TRANSIENT.equals(modifier) ||
          PsiModifier.STRICTFP.equals(modifier) || PsiModifier.SYNCHRONIZED.equals(modifier)) {
        isAllowed = modifierOwnerParent instanceof PsiClass && !((PsiClass)modifierOwnerParent).isInterface();
      }
    }
    else if (modifierOwner instanceof PsiClassInitializer) {
      isAllowed = PsiModifier.STATIC.equals(modifier);
    }
    else if (modifierOwner instanceof PsiLocalVariable || modifierOwner instanceof PsiParameter) {
      isAllowed = PsiModifier.FINAL.equals(modifier);
    }
    else if (modifierOwner instanceof PsiReceiverParameter || modifierOwner instanceof PsiRecordComponent) {
      isAllowed = false;
    }

    isAllowed &= incompatibles != null;
    if (!isAllowed) {
      String message = JavaErrorBundle.message("modifier.not.allowed", modifier);
      HighlightInfo.Builder highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(message);
      IntentionAction action = fix != null ? fix : getFixFactory()
        .createModifierListFix(modifierList, modifier, false, false);
      highlightInfo.registerFix(action, null, null, null, null);
      return highlightInfo;
    }

    return null;
  }

  /**
   * @deprecated use {@link #checkLiteralExpressionParsingError(PsiLiteralExpression, LanguageLevel, PsiFile, Ref)}
   */
  @Deprecated
  public static HighlightInfo checkLiteralExpressionParsingError(@NotNull PsiLiteralExpression expression,
                                                                 @NotNull LanguageLevel level,
                                                                 @Nullable PsiFile file) {
    HighlightInfo.Builder builder = checkLiteralExpressionParsingError(expression, level, file, null);
    return builder == null ? null : builder.create();
  }

  public static HighlightInfo.Builder checkLiteralExpressionParsingError(@NotNull PsiLiteralExpression expression,
                                                                 @NotNull LanguageLevel level,
                                                                 @Nullable PsiFile file, @Nullable Ref<? super String> description) {
    PsiElement literal = expression.getFirstChild();
    assert literal instanceof PsiJavaToken : literal;
    IElementType type = ((PsiJavaToken)literal).getTokenType();
    if (type == JavaTokenType.TRUE_KEYWORD || type == JavaTokenType.FALSE_KEYWORD || type == JavaTokenType.NULL_KEYWORD) {
      return null;
    }

    boolean isInt = ElementType.INTEGER_LITERALS.contains(type);
    boolean isFP = ElementType.REAL_LITERALS.contains(type);
    String rawText = isInt || isFP ? StringUtil.toLowerCase(literal.getText()) : literal.getText();
    String text = parseUnicodeEscapes(rawText, null);
    Object value = expression.getValue();

    if (file != null) {
      if (isFP) {
        if (text.startsWith(PsiLiteralUtil.HEX_PREFIX)) {
          HighlightInfo.Builder info = checkFeature(expression, HighlightingFeature.HEX_FP_LITERALS, level, file);
          if (info != null) {
            if (description != null) {
              description.set(getUnsupportedFeatureMessage(HighlightingFeature.HEX_FP_LITERALS, level, file));
            }
            return info;
          }
        }
      }
      if (isInt) {
        if (text.startsWith(PsiLiteralUtil.BIN_PREFIX)) {
          HighlightInfo.Builder info = checkFeature(expression, HighlightingFeature.BIN_LITERALS, level, file);
          if (info != null) {
            if (description != null) {
              description.set(getUnsupportedFeatureMessage(HighlightingFeature.BIN_LITERALS, level, file));
            }
            return info;
          }
        }
      }
      if (isInt || isFP) {
        if (text.contains("_")) {
          HighlightInfo.Builder info = checkFeature(expression, HighlightingFeature.UNDERSCORES, level, file);
          if (info != null) {
            if (description != null) {
              description.set(getUnsupportedFeatureMessage(HighlightingFeature.UNDERSCORES, level, file));
            }
            return info;
          }
          info = checkUnderscores(expression, text, isInt);
          if (info != null) {
            if (description != null) {
              description.set(JavaErrorBundle.message("illegal.underscore"));
            }
            return info;
          }
        }
      }
    }

    PsiElement parent = expression.getParent();
    if (type == JavaTokenType.INTEGER_LITERAL) {
      String cleanText = StringUtil.replace(text, "_", "");
      //literal 2147483648 may appear only as the operand of the unary negation operator -.
      if (!(cleanText.equals(PsiLiteralUtil._2_IN_31) &&
            parent instanceof PsiPrefixExpression &&
            ((PsiPrefixExpression)parent).getOperationTokenType() == JavaTokenType.MINUS)) {
        if (cleanText.equals(PsiLiteralUtil.HEX_PREFIX)) {
          String message = JavaErrorBundle.message("hexadecimal.numbers.must.contain.at.least.one.hexadecimal.digit");
          if (description != null) {
            description.set(message);
          }
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
        }
        if (cleanText.equals(PsiLiteralUtil.BIN_PREFIX)) {
          String message = JavaErrorBundle.message("binary.numbers.must.contain.at.least.one.hexadecimal.digit");
          if (description != null) {
            description.set(message);
          }
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
        }
        if (value == null || cleanText.equals(PsiLiteralUtil._2_IN_31)) {
          String message = JavaErrorBundle.message("integer.number.too.large");
          if (description != null) {
            description.set(message);
          }
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
        }
      }
    }
    else if (type == JavaTokenType.LONG_LITERAL) {
      String cleanText = StringUtil.replace(StringUtil.trimEnd(text, 'l'), "_", "");
      //literal 9223372036854775808L may appear only as the operand of the unary negation operator -.
      if (!(cleanText.equals(PsiLiteralUtil._2_IN_63) &&
            parent instanceof PsiPrefixExpression &&
            ((PsiPrefixExpression)parent).getOperationTokenType() == JavaTokenType.MINUS)) {
        if (cleanText.equals(PsiLiteralUtil.HEX_PREFIX)) {
          String message = JavaErrorBundle.message("hexadecimal.numbers.must.contain.at.least.one.hexadecimal.digit");
          if (description != null) {
            description.set(message);
          }
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
        }
        if (cleanText.equals(PsiLiteralUtil.BIN_PREFIX)) {
          String message = JavaErrorBundle.message("binary.numbers.must.contain.at.least.one.hexadecimal.digit");
          if (description != null) {
            description.set(message);
          }
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
        }
        if (value == null || cleanText.equals(PsiLiteralUtil._2_IN_63)) {
          String message = JavaErrorBundle.message("long.number.too.large");
          if (description != null) {
            description.set(message);
          }
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
        }
      }
    }
    else if (isFP) {
      if (value == null) {
        String message = JavaErrorBundle.message("malformed.floating.point.literal");
        if (description != null) {
          description.set(message);
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
      }
    }
    else if (type == JavaTokenType.CHARACTER_LITERAL) {
      if (!StringUtil.startsWithChar(text, '\'')) {
        return null;
      }
      if (!StringUtil.endsWithChar(text, '\'') || text.length() == 1) {
        String message = JavaErrorBundle.message("unclosed.char.literal");
        if (description != null) {
          description.set(message);
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
      }

      int rawLength = rawText.length();
      StringBuilder chars = new StringBuilder(rawLength);
      int[] offsets = new int[rawLength + 1];
      final boolean success = CodeInsightUtilCore.parseStringCharacters(rawText, chars, offsets, false);
      if (!success) {
        String message = JavaErrorBundle.message("illegal.escape.character.in.character.literal");
        if (description != null) {
          description.set(message);
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(expression, calculateErrorRange(rawText, offsets[chars.length()]))
          .descriptionAndTooltip(message)
          ;
      }
      int length = chars.length();
      if (length > 3) {
        String message = JavaErrorBundle.message("too.many.characters.in.character.literal");
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
        IntentionAction action = getFixFactory().createConvertToStringLiteralAction();
        info.registerFix(action, null, null, null, null);
        if (description != null) {
          description.set(message);
        }
        return info;
      }
      else if (length == 2) {
        String message = JavaErrorBundle.message("empty.character.literal");
        if (description != null) {
          description.set(message);
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
      }
      if (file != null && containsUnescaped(text, "\\s")) {
        HighlightInfo.Builder info = checkFeature(expression, HighlightingFeature.TEXT_BLOCK_ESCAPES, level, file);
        if (info != null) {
          if (description != null) {
            description.set(getUnsupportedFeatureMessage(HighlightingFeature.TEXT_BLOCK_ESCAPES, level, file));
          }
          return info;
        }
      }
    }
    else if (type == JavaTokenType.STRING_LITERAL || type == JavaTokenType.TEXT_BLOCK_LITERAL) {
      if (type == JavaTokenType.STRING_LITERAL) {
        for (PsiElement element = expression.getFirstChild(); element != null; element = element.getNextSibling()) {
          if (element instanceof OuterLanguageElement) {
            return null;
          }
        }

        if (!StringUtil.startsWithChar(text, '\"')) return null;
        if (StringUtil.endsWithChar(text, '\"')) {
          if (text.length() == 1) {
            String message = JavaErrorBundle.message("illegal.line.end.in.string.literal");
            if (description != null) {
              description.set(message);
            }
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
          }
        }
        else {
          String message = JavaErrorBundle.message("illegal.line.end.in.string.literal");
          if (description != null) {
            description.set(message);
          }
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
        }
        int length = rawText.length();
        StringBuilder chars = new StringBuilder(length);
        int[] offsets = new int[length + 1];
        boolean success = CodeInsightUtilCore.parseStringCharacters(rawText, chars, offsets, false);
        if (!success) {
          String message = JavaErrorBundle.message("illegal.escape.character.in.string.literal");
          if (description != null) {
            description.set(message);
          }
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(expression, calculateErrorRange(rawText, offsets[chars.length()]))
            .descriptionAndTooltip(message);
        }
        if (file != null && containsUnescaped(text, "\\s")) {
          HighlightInfo.Builder info = checkFeature(expression, HighlightingFeature.TEXT_BLOCK_ESCAPES, level, file);
          if (info != null) {
            if (description != null) {
              description.set(getUnsupportedFeatureMessage(HighlightingFeature.TEXT_BLOCK_ESCAPES, level, file));
            }
            return info;
          }
        }
      }
      else {
        if (!text.endsWith("\"\"\"")) {
          String message = JavaErrorBundle.message("text.block.unclosed");
          if (description != null) {
            description.set(message);
          }
          int p = expression.getTextRange().getEndOffset();
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(p, p).endOfLine().descriptionAndTooltip(message);
        }
        else if (text.length() > 3) {
          int i = 3;
          char c = text.charAt(i);
          while (PsiLiteralUtil.isTextBlockWhiteSpace(c)) {
            i++;
            c = text.charAt(i);
          }
          if (c != '\n' && c != '\r') {
            String message = JavaErrorBundle.message("text.block.new.line");
            if (description != null) {
              description.set(message);
            }
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
          }
          final int rawLength = rawText.length();
          StringBuilder chars = new StringBuilder(rawLength);
          int[] offsets = new int[rawLength + 1];
          boolean success = CodeInsightUtilCore.parseStringCharacters(rawText, chars, offsets, true);
          if (!success) {
            String message = JavaErrorBundle.message("illegal.escape.character.in.string.literal");
            if (description != null) {
              description.set(message);
            }
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
              .range(expression, calculateErrorRange(rawText, offsets[chars.length()]))
              .descriptionAndTooltip(message);
          }
        }
      }
    }

    if (value instanceof Float) {
      Float number = (Float)value;
      if (number.isInfinite()) {
        String message = JavaErrorBundle.message("floating.point.number.too.large");
        if (description != null) {
          description.set(message);
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
      }
      if (number.floatValue() == 0 && !TypeConversionUtil.isFPZero(text)) {
        String message = JavaErrorBundle.message("floating.point.number.too.small");
        if (description != null) {
          description.set(message);
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
      }
    }
    else if (value instanceof Double) {
      Double number = (Double)value;
      if (number.isInfinite()) {
        String message = JavaErrorBundle.message("floating.point.number.too.large");
        if (description != null) {
          description.set(message);
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
      }
      if (number.doubleValue() == 0 && !TypeConversionUtil.isFPZero(text)) {
        String message = JavaErrorBundle.message("floating.point.number.too.small");
        if (description != null) {
          description.set(message);
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
      }
    }

    return null;
  }

  @NotNull
  private static TextRange calculateErrorRange(@NotNull String rawText, int start) {
    int end;
    if (rawText.charAt(start + 1) == 'u') {
      end = start + 2;
      while (rawText.charAt(end) == 'u') end++;
      end += 4;
    }
    else end = start + 2;
    return new TextRange(start, end);
  }

  private static boolean containsUnescaped(@NotNull String text, @NotNull String subText) {
    int start = 0;
    while ((start = StringUtil.indexOf(text, subText, start)) != -1) {
      int nSlashes = 0;
      for (int pos = start - 1; pos >= 0; pos--) {
        if (text.charAt(pos) != '\\') break;
        nSlashes++;
      }
      if (nSlashes % 2 == 0) return true;
      start += subText.length();
    }
    return false;
  }

  private static final Pattern FP_LITERAL_PARTS =
    Pattern.compile("(?:" +
                    "0x([_\\p{XDigit}]*)\\.?([_\\p{XDigit}]*)p[+-]?([_\\d]*)" +
                    "|" +
                    "([_\\d]*)\\.?([_\\d]*)e?[+-]?([_\\d]*)" +
                    ")[fd]?");

  private static HighlightInfo.Builder checkUnderscores(@NotNull PsiElement expression, @NotNull String text, boolean isInt) {
    String[] parts = ArrayUtilRt.EMPTY_STRING_ARRAY;

    if (isInt) {
      int start = 0;
      if (text.startsWith(PsiLiteralUtil.HEX_PREFIX) || text.startsWith(PsiLiteralUtil.BIN_PREFIX)) start += 2;
      int end = text.length();
      if (StringUtil.endsWithChar(text, 'l')) --end;
      parts = new String[]{text.substring(start, end)};
    }
    else {
      Matcher matcher = FP_LITERAL_PARTS.matcher(text);
      if (matcher.matches()) {
        parts = new String[matcher.groupCount()];
        for (int i = 0; i < matcher.groupCount(); i++) {
          parts[i] = matcher.group(i + 1);
        }
      }
    }

    for (String part : parts) {
      if (part != null && (StringUtil.startsWithChar(part, '_') || StringUtil.endsWithChar(part, '_'))) {
        String message = JavaErrorBundle.message("illegal.underscore");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkMustBeBoolean(@NotNull PsiExpression expr, @Nullable PsiType type) {
    PsiElement parent = expr.getParent();
    if (parent instanceof PsiIfStatement ||
        parent instanceof PsiConditionalLoopStatement && expr.equals(((PsiConditionalLoopStatement)parent).getCondition())) {
      if (expr.getNextSibling() instanceof PsiErrorElement) return null;

      if (!TypeConversionUtil.isBooleanType(type)) {
        return createMustBeBooleanInfo(expr, type);
      }
    }
    return null;
  }

  @NotNull
  private static HighlightInfo.Builder createMustBeBooleanInfo(@NotNull PsiExpression expr, @Nullable PsiType type) {
    HighlightInfo.Builder info = createIncompatibleTypeHighlightInfo(PsiTypes.booleanType(), type, expr.getTextRange(), 0);
    if (expr instanceof PsiMethodCallExpression methodCall) {
      PsiMethod method = methodCall.resolveMethod();
      if (method != null) {
        IntentionAction action = getFixFactory().createMethodReturnFix(method, PsiTypes.booleanType(), true);
        info.registerFix(action, null, null, null, null);
      }
    }
    else if (expr instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)expr).getOperationTokenType() == JavaTokenType.EQ) {
      IntentionAction action = getFixFactory().createAssignmentToComparisonFix((PsiAssignmentExpression)expr);
      info.registerFix(action, null, null, null, null);
    }
    return info;
  }


  @NotNull
  static Set<PsiClassType> collectUnhandledExceptions(@NotNull PsiTryStatement statement) {
    Set<PsiClassType> thrownTypes = new HashSet<>();

    PsiCodeBlock tryBlock = statement.getTryBlock();
    if (tryBlock != null) {
      thrownTypes.addAll(ExceptionUtil.collectUnhandledExceptions(tryBlock, tryBlock));
    }

    PsiResourceList resources = statement.getResourceList();
    if (resources != null) {
      thrownTypes.addAll(ExceptionUtil.collectUnhandledExceptions(resources, resources));
    }

    return thrownTypes;
  }

  static void checkExceptionThrownInTry(@NotNull PsiParameter parameter,
                                        @NotNull Set<? extends PsiClassType> thrownTypes,
                                        @NotNull HighlightInfoHolder holder) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiCatchSection)) return;

    PsiType caughtType = parameter.getType();
    if (caughtType instanceof PsiClassType) {
      HighlightInfo.Builder info = checkSimpleCatchParameter(parameter, thrownTypes, (PsiClassType)caughtType);
      if (info != null) {
        holder.add(info.create());
      }
      return;
    }
    if (caughtType instanceof PsiDisjunctionType) {
      checkMultiCatchParameter(parameter, thrownTypes, holder);
    }
  }

  private static HighlightInfo.Builder checkSimpleCatchParameter(@NotNull PsiParameter parameter,
                                                         @NotNull Collection<? extends PsiClassType> thrownTypes,
                                                         @NotNull PsiClassType caughtType) {
    if (ExceptionUtil.isUncheckedExceptionOrSuperclass(caughtType)) return null;

    for (PsiClassType exceptionType : thrownTypes) {
      if (exceptionType.isAssignableFrom(caughtType) || caughtType.isAssignableFrom(exceptionType)) return null;
    }

    String description = JavaErrorBundle.message("exception.never.thrown.try", JavaHighlightUtil.formatType(caughtType));
    HighlightInfo.Builder errorResult =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameter).descriptionAndTooltip(description);
    IntentionAction action = getFixFactory().createDeleteCatchFix(parameter);
    errorResult.registerFix(action, null, null, null, null);
    return errorResult;
  }

  private static void checkMultiCatchParameter(@NotNull PsiParameter parameter,
                                               @NotNull Collection<? extends PsiClassType> thrownTypes,
                                               @NotNull HighlightInfoHolder holder) {
    List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);

    for (PsiTypeElement typeElement : typeElements) {
      PsiType catchType = typeElement.getType();
      if (catchType instanceof PsiClassType && ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)catchType)) continue;

      boolean used = false;
      for (PsiClassType exceptionType : thrownTypes) {
        if (exceptionType.isAssignableFrom(catchType) || catchType.isAssignableFrom(exceptionType)) {
          used = true;
          break;
        }
      }
      if (!used) {
        String description = JavaErrorBundle.message("exception.never.thrown.try", JavaHighlightUtil.formatType(catchType));
        HighlightInfo.Builder builder =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description);
        IntentionAction action = getFixFactory().createDeleteMultiCatchFix(typeElement);
        builder.registerFix(action, null, null, null, null);
        holder.add(builder.create());
      }
    }
  }


  static void checkWithImprovedCatchAnalysis(@NotNull PsiParameter parameter,
                                             @NotNull Collection<? extends PsiClassType> thrownInTryStatement,
                                             @NotNull PsiFile containingFile, @NotNull HighlightInfoHolder holder) {
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiCatchSection catchSection)) return;

    PsiCatchSection[] allCatchSections = catchSection.getTryStatement().getCatchSections();
    int idx = ArrayUtilRt.find(allCatchSections, catchSection);
    if (idx <= 0) return;

    Collection<PsiClassType> thrownTypes = new HashSet<>(thrownInTryStatement);
    PsiManager manager = containingFile.getManager();
    GlobalSearchScope parameterResolveScope = parameter.getResolveScope();
    thrownTypes.add(PsiType.getJavaLangError(manager, parameterResolveScope));
    thrownTypes.add(PsiType.getJavaLangRuntimeException(manager, parameterResolveScope));

    List<PsiTypeElement> parameterTypeElements = PsiUtil.getParameterTypeElements(parameter);
    boolean isMultiCatch = parameterTypeElements.size() > 1;
    for (PsiTypeElement catchTypeElement : parameterTypeElements) {
      PsiType catchType = catchTypeElement.getType();
      if (ExceptionUtil.isGeneralExceptionType(catchType)) continue;

      // collect exceptions caught by this type
      List<PsiClassType> caught = new ArrayList<>();
      for (PsiClassType t : thrownTypes) {
        if (catchType.isAssignableFrom(t) || t.isAssignableFrom(catchType)) {
          caught.add(t);
        }
      }
      if (caught.isEmpty()) continue;
      Collection<PsiClassType> caughtCopy = new HashSet<>(caught);

      // exclude all caught by previous catch sections
      for (int i = 0; i < idx; i++) {
        PsiParameter prevCatchParameter = allCatchSections[i].getParameter();
        if (prevCatchParameter == null) continue;
        for (PsiTypeElement prevCatchTypeElement : PsiUtil.getParameterTypeElements(prevCatchParameter)) {
          PsiType prevCatchType = prevCatchTypeElement.getType();
          caught.removeIf(prevCatchType::isAssignableFrom);
          if (caught.isEmpty()) break;
        }
      }

      // check & warn
      if (caught.isEmpty()) {
        String message = JavaErrorBundle.message("exception.already.caught.warn", formatTypes(caughtCopy), caughtCopy.size());
        HighlightInfo.Builder builder =
          HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(catchSection).descriptionAndTooltip(message);
        IntentionAction action = isMultiCatch ? 
                                 getFixFactory().createDeleteMultiCatchFix(catchTypeElement) : 
                                 getFixFactory().createDeleteCatchFix(parameter);
        builder.registerFix(action, null, null, null, null);
        holder.add(builder.create());
      }
    }
  }


  static HighlightInfo.Builder checkNotAStatement(@NotNull PsiStatement statement) {
    if (!PsiUtil.isStatement(statement)) {
      PsiElement anchor = statement;
      if (PsiUtilCore.hasErrorElementChild(statement)) {
        boolean allowedError = false;
        if (statement instanceof PsiExpressionStatement) {
          PsiElement[] children = statement.getChildren();
          if (children[0] instanceof PsiExpression && children[1] instanceof PsiErrorElement &&
              ((PsiErrorElement)children[1]).getErrorDescription().equals(JavaPsiBundle.message("expected.semicolon"))) {
            allowedError = true;
            anchor = children[0];
          }
        }
        if (!allowedError) return null;
      }
      boolean isDeclarationNotAllowed = false;
      if (statement instanceof PsiDeclarationStatement) {
        PsiElement parent = statement.getParent();
        isDeclarationNotAllowed = parent instanceof PsiIfStatement || parent instanceof PsiLoopStatement;
      }
      String description = JavaErrorBundle.message(isDeclarationNotAllowed ? "declaration.not.allowed" : "not.a.statement");
      HighlightInfo.Builder error =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(anchor).descriptionAndTooltip(description);
      if (statement instanceof PsiExpressionStatement) {
        List<IntentionAction> registrar = new ArrayList<>();
        HighlightFixUtil.registerFixesForExpressionStatement(statement, registrar);
        QuickFixAction.registerQuickFixActions(error, null, registrar);
        IntentionAction action = PriorityIntentionActionWrapper
          .lowPriority(getFixFactory().createDeleteSideEffectAwareFix((PsiExpressionStatement)statement));
        error.registerFix(action, null, null, null, null);
      }
      return error;
    }
    return null;
  }

  static void checkSwitchExpressionReturnTypeCompatible(@NotNull PsiSwitchExpression switchExpression,
                                                        @NotNull HighlightInfoHolder holder) {
    if (!PsiPolyExpressionUtil.isPolyExpression(switchExpression)) {
      return;
    }
    PsiType switchExpressionType = switchExpression.getType();
    if (switchExpressionType != null) {
      for (PsiExpression expression : PsiUtil.getSwitchResultExpressions(switchExpression)) {
        PsiType expressionType = expression.getType();
        if (expressionType != null && !TypeConversionUtil.areTypesAssignmentCompatible(switchExpressionType, expression)) {
          String text = JavaErrorBundle
            .message("bad.type.in.switch.expression", expressionType.getCanonicalText(), switchExpressionType.getCanonicalText());
          HighlightInfo.Builder info =
            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(text);
          registerChangeTypeFix(info, switchExpression, expressionType);
          holder.add(info.create());
        }
      }

      if (PsiTypes.voidType().equals(switchExpressionType)) {
        String text = JavaErrorBundle.message("switch.expression.cannot.be.void");
        holder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(switchExpression.getFirstChild()).descriptionAndTooltip(text).create());
      }
    }
  }

  static void registerChangeTypeFix(@Nullable HighlightInfo.Builder info,
                                    @NotNull PsiExpression expression,
                                    @NotNull PsiType expectedType) {
    if (info == null) return;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiReturnStatement) {
      PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, false, PsiLambdaExpression.class);
      if (method != null) {
        registerReturnTypeFixes(info, method, expectedType);
      }
    }
    else if (parent instanceof PsiLocalVariable) {
      HighlightFixUtil.registerChangeVariableTypeFixes((PsiLocalVariable)parent, expectedType, null, info);
    }
    else if (parent instanceof PsiAssignmentExpression) {
      HighlightFixUtil.registerChangeVariableTypeFixes(((PsiAssignmentExpression)parent).getLExpression(), expectedType, null, info);
    }
  }

  static HighlightInfo.Builder checkRecordComponentName(@NotNull PsiRecordComponent component) {
    PsiIdentifier identifier = component.getNameIdentifier();
    if (identifier != null) {
      String name = identifier.getText();
      if (RESTRICTED_RECORD_COMPONENT_NAMES.contains(name)) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier)
          .descriptionAndTooltip(JavaErrorBundle.message("record.component.restricted.name", name));
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkRecordComponentVarArg(@NotNull PsiRecordComponent recordComponent) {
    if (recordComponent.isVarArgs() && PsiTreeUtil.getNextSiblingOfType(recordComponent, PsiRecordComponent.class) != null) {
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(recordComponent)
        .descriptionAndTooltip(JavaErrorBundle.message("record.component.vararg.not.last"));
      IntentionAction action = getFixFactory().createMakeVarargParameterLastFix(recordComponent);
      info.registerFix(action, null, null, null, null);
      return info;
    }
    return null;
  }

  static HighlightInfo.Builder checkCStyleDeclaration(@NotNull PsiVariable variable) {
    PsiIdentifier identifier = variable.getNameIdentifier();
    if (identifier == null) return null;
    PsiElement start = null;
    PsiElement end = null;
    for (PsiElement element = identifier.getNextSibling(); element != null; element = element.getNextSibling()) {
      if (PsiUtil.isJavaToken(element, BRACKET_TOKENS)) {
        if (start == null) start = element;
        end = element;
      }
    }
    if (start != null) {
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(variable, start.getTextRange().getStartOffset(), end.getTextRange().getEndOffset())
        .descriptionAndTooltip(variable instanceof PsiRecordComponent
          ? JavaErrorBundle.message("record.component.cstyle.declaration")
          : JavaErrorBundle.message("vararg.cstyle.array.declaration"));
      var action = new NormalizeBracketsFix(variable);
      info.registerFix(action, null, null, null, null);
      return info;
    }
    return null;
  }

  static HighlightInfo.Builder checkRecordAccessorReturnType(@NotNull PsiRecordComponent component) {
    String componentName = component.getName();
    PsiTypeElement typeElement = component.getTypeElement();
    if (typeElement == null) return null;
    PsiClass containingClass = component.getContainingClass();
    if (containingClass == null) return null;
    PsiMethod[] methods = containingClass.findMethodsByName(componentName, false);
    for (PsiMethod method : methods) {
      if (method instanceof LightRecordMethod) {
        List<HierarchicalMethodSignature> superSignatures =
          PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method, method.getResolveScope()).getSuperSignatures();
        MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
        return HighlightMethodUtil.checkMethodIncompatibleReturnType(signature, superSignatures, true, typeElement.getTextRange(),
                                                                     null);
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkInstanceOfPatternSupertype(@NotNull PsiInstanceOfExpression expression) {
    PsiTypeTestPattern pattern = getTypeTestPattern(expression.getPattern());
    if (pattern == null) return null;
    PsiPatternVariable variable = pattern.getPatternVariable();
    if (variable == null) return null;
    PsiTypeElement typeElement = pattern.getCheckType();
    if (typeElement == null) return null;
    PsiType checkType = typeElement.getType();
    PsiType expressionType = expression.getOperand().getType();
    if (expressionType != null && checkType.isAssignableFrom(expressionType)) {
      String description =
        checkType.equals(expressionType) ?
        JavaErrorBundle.message("instanceof.pattern.equals", checkType.getPresentableText()) :
        JavaErrorBundle.message("instanceof.pattern.supertype", checkType.getPresentableText(), expressionType.getPresentableText());
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description);
      if (!VariableAccessUtils.variableIsUsed(variable, variable.getDeclarationScope())) {
        var action = new RedundantInstanceofFix(expression);
        info.registerFix(action, null, null, null, null);
      }
      return info;
    }
    return null;
  }

  @Contract(value = "null -> null", pure = true)
  private static @Nullable PsiTypeTestPattern getTypeTestPattern(@Nullable PsiPattern expressionPattern) {
    PsiPattern innerMostPattern = JavaPsiPatternUtil.skipParenthesizedPatternDown(expressionPattern);
    if (innerMostPattern == null) return null;

    PsiTypeTestPattern pattern = tryCast(innerMostPattern, PsiTypeTestPattern.class);
    if (pattern != null) return pattern;

    PsiGuardedPattern guardedPattern = tryCast(innerMostPattern, PsiGuardedPattern.class);
    if (guardedPattern == null) return null;

    Object condition = ExpressionUtils.computeConstantExpression(guardedPattern.getGuardingExpression());
    if (!Boolean.TRUE.equals(condition)) return null;

    PsiPattern patternInGuard = JavaPsiPatternUtil.skipParenthesizedPatternDown(guardedPattern.getPrimaryPattern());
    if (patternInGuard == null || patternInGuard instanceof PsiTypeTestPattern) return (PsiTypeTestPattern)patternInGuard;

    return getTypeTestPattern(patternInGuard);
  }

  static HighlightInfo.Builder checkPolyadicOperatorApplicable(@NotNull PsiPolyadicExpression expression) {
    PsiExpression[] operands = expression.getOperands();

    PsiType lType = operands[0].getType();
    IElementType operationSign = expression.getOperationTokenType();
    for (int i = 1; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      PsiType rType = operand.getType();
      if (!TypeConversionUtil.isBinaryOperatorApplicable(operationSign, lType, rType, false)) {
        PsiJavaToken token = expression.getTokenBeforeOperand(operand);
        assert token != null : expression;
        String message = JavaErrorBundle.message("binary.operator.not.applicable", token.getText(),
                                                 JavaHighlightUtil.formatType(lType),
                                                 JavaHighlightUtil.formatType(rType));
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
      }
      lType = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, operationSign, true);
    }

    return null;
  }


  static HighlightInfo.Builder checkUnaryOperatorApplicable(@NotNull PsiJavaToken token, @Nullable PsiExpression expression) {
    if (expression != null && !TypeConversionUtil.isUnaryOperatorApplicable(token, expression)) {
      PsiType type = expression.getType();
      if (type == null) return null;
      String message = JavaErrorBundle.message("unary.operator.not.applicable", token.getText(), JavaHighlightUtil.formatType(type));

      PsiElement parentExpr = token.getParent();
      HighlightInfo.Builder highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parentExpr).descriptionAndTooltip(message);
      if (parentExpr instanceof PsiPrefixExpression && token.getTokenType() == JavaTokenType.EXCL) {
        IntentionAction action1 = getFixFactory().createNegationBroadScopeFix((PsiPrefixExpression)parentExpr);
        highlightInfo.registerFix(action1, null, null, null, null);
        if (expression instanceof PsiMethodCallExpression) {
          PsiMethod method = ((PsiMethodCallExpression)expression).resolveMethod();
          if (method != null) {
            IntentionAction action = getFixFactory().createMethodReturnFix(method, PsiTypes.booleanType(), true);
            highlightInfo.registerFix(action, null, null, null, null);
          }
        }
      }
      return highlightInfo;
    }
    return null;
  }

  static HighlightInfo.Builder checkThisOrSuperExpressionInIllegalContext(@NotNull PsiExpression expr,
                                                                  @Nullable PsiJavaCodeReferenceElement qualifier,
                                                                  @NotNull LanguageLevel languageLevel) {
    if (expr instanceof PsiSuperExpression) {
      PsiElement parent = expr.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        // like in 'Object o = super;'
        int o = expr.getTextRange().getEndOffset();
        String description = JavaErrorBundle.message("dot.expected.after.super.or.this");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(o, o + 1).descriptionAndTooltip(description);
      }
    }

    PsiClass aClass;
    if (qualifier != null) {
      PsiElement resolved = qualifier.advancedResolve(true).getElement();
      if (resolved != null && !(resolved instanceof PsiClass)) {
        String description = JavaErrorBundle.message("class.expected");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description);
      }
      aClass = (PsiClass)resolved;
    }
    else {
      aClass = getContainingClass(expr);
    }
    if (aClass == null) return null;

    if (!InheritanceUtil.hasEnclosingInstanceInScope(aClass, expr, false, false)) {
      if (!resolvesToImmediateSuperInterface(expr, qualifier, aClass, languageLevel)) {
        return HighlightClassUtil.checkIllegalEnclosingUsage(expr, null, aClass, expr);
      }
      if (expr instanceof PsiSuperExpression) {
        PsiElement resolved = ((PsiReferenceExpression)expr.getParent()).resolve();
        //15.11.2
        //The form T.super.Identifier refers to the field named Identifier of the lexically enclosing instance corresponding to T,
        //but with that instance viewed as an instance of the superclass of T.
        if (resolved instanceof PsiField) {
          String description = JavaErrorBundle.message("is.not.an.enclosing.class", formatClass(aClass));
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(description);
        }
      }
    }

    if (qualifier != null && aClass.isInterface() && expr instanceof PsiSuperExpression && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      //15.12.1 for method invocation expressions; 15.13 for method references
      //If TypeName denotes an interface, I, then let T be the type declaration immediately enclosing the method reference expression.
      //It is a compile-time error if I is not a direct superinterface of T,
      //or if there exists some other direct superclass or direct superinterface of T, J, such that J is a subtype of I.
      PsiClass classT = getContainingClass(expr);
      if (classT != null) {
        PsiElement parent = expr.getParent();
        PsiElement resolved = parent instanceof PsiReferenceExpression ? ((PsiReferenceExpression)parent).resolve() : null;

        PsiClass containingClass =
          ObjectUtils.notNull(resolved instanceof PsiMethod ? ((PsiMethod)resolved).getContainingClass() : null, aClass);
        for (PsiClass superClass : classT.getSupers()) {
          if (superClass.isInheritor(containingClass, true)) {
            if (superClass.isInheritor(aClass, true)) {
              return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(qualifier)
                .descriptionAndTooltip(
                  JavaErrorBundle.message("bad.qualifier.in.super.method.reference.extended", format(containingClass), formatClass(superClass)))
                ;
            }
            else if (resolved instanceof PsiMethod &&
                     MethodSignatureUtil.findMethodBySuperMethod(superClass, (PsiMethod)resolved, true) != resolved) {
              return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(qualifier)
                .descriptionAndTooltip(
                  JavaErrorBundle.message("bad.qualifier.in.super.method.reference.overridden", ((PsiMethod)resolved).getName(), formatClass(superClass)))
                ;
            }

          }
        }

        if (!classT.isInheritor(aClass, false)) {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(qualifier)
            .descriptionAndTooltip(JavaErrorBundle.message("no.enclosing.instance.in.scope", format(aClass)));
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiClass getContainingClass(@NotNull PsiExpression expr) {
    PsiClass aClass = PsiTreeUtil.getParentOfType(expr, PsiClass.class);
    while (aClass instanceof PsiAnonymousClass && PsiTreeUtil.isAncestor(((PsiAnonymousClass)aClass).getArgumentList(), expr, true)) {
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    }
    return aClass;
  }

  static HighlightInfo.Builder checkUnqualifiedSuperInDefaultMethod(@NotNull LanguageLevel languageLevel,
                                                            @NotNull PsiReferenceExpression expr,
                                                            @Nullable PsiExpression qualifier) {
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8) && qualifier instanceof PsiSuperExpression) {
      PsiMethod method = PsiTreeUtil.getParentOfType(expr, PsiMethod.class);
      if (method != null && method.hasModifierProperty(PsiModifier.DEFAULT) && ((PsiSuperExpression)qualifier).getQualifier() == null) {
        String description = JavaErrorBundle.message("unqualified.super.disallowed");
        HighlightInfo.Builder builder =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(description);
        QualifySuperArgumentFix.registerQuickFixAction((PsiSuperExpression)qualifier, builder);
        return builder;
      }
    }
    return null;
  }

  private static boolean resolvesToImmediateSuperInterface(@NotNull PsiExpression expr,
                                                           @Nullable PsiJavaCodeReferenceElement qualifier,
                                                           @NotNull PsiClass aClass,
                                                           @NotNull LanguageLevel languageLevel) {
    if (!(expr instanceof PsiSuperExpression) || qualifier == null || !languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) return false;
    PsiType superType = expr.getType();
    if (!(superType instanceof PsiClassType)) return false;
    PsiClass superClass = ((PsiClassType)superType).resolve();
    return aClass.equals(superClass) && PsiUtil.getEnclosingStaticElement(expr, PsiTreeUtil.getParentOfType(expr, PsiClass.class)) == null;
  }

  @NotNull
  static @NlsContexts.DetailedDescription String staticContextProblemDescription(@NotNull PsiElement refElement) {
    String type = JavaElementKind.fromElement(refElement).lessDescriptive().subject();
    String name = HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY);
    return JavaErrorBundle.message("non.static.symbol.referenced.from.static.context", type, name);
  }

  @NotNull
  static @NlsContexts.DetailedDescription String accessProblemDescription(@NotNull PsiElement ref,
                                                                          @NotNull PsiElement resolved,
                                                                          @NotNull JavaResolveResult result) {
    return accessProblemDescriptionAndFixes(ref, resolved, result).first;
  }

  @NotNull
  static Pair<@Nls String, List<IntentionAction>> accessProblemDescriptionAndFixes(@NotNull PsiElement ref,
                                                                                   @NotNull PsiElement resolved,
                                                                                   @NotNull JavaResolveResult result) {
    assert resolved instanceof PsiModifierListOwner : resolved;
    PsiModifierListOwner refElement = (PsiModifierListOwner)resolved;
    String symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());

    if (refElement.hasModifierProperty(PsiModifier.PRIVATE)) {
      String containerName = getContainerName(refElement, result.getSubstitutor());
      return Pair.pair(JavaErrorBundle.message("private.symbol", symbolName, containerName), null);
    }

    if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
      String containerName = getContainerName(refElement, result.getSubstitutor());
      return Pair.pair(JavaErrorBundle.message("protected.symbol", symbolName, containerName), null);
    }

    PsiClass packageLocalClass = HighlightFixUtil.getPackageLocalClassInTheMiddle(ref);
    if (packageLocalClass != null) {
      refElement = packageLocalClass;
      symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());
    }

    if (refElement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) || packageLocalClass != null) {
      String containerName = getContainerName(refElement, result.getSubstitutor());
      return Pair.pair(JavaErrorBundle.message("package.local.symbol", symbolName, containerName), null);
    }

    String containerName = getContainerName(refElement, result.getSubstitutor());
    ErrorWithFixes problem = checkModuleAccess(resolved, ref, symbolName, containerName);
    if (problem != null) return Pair.pair(problem.message, problem.fixes);
    return Pair.pair(JavaErrorBundle.message("visibility.access.problem", symbolName, containerName), null);
  }

  private static ErrorWithFixes checkModuleAccess(@NotNull PsiElement target,
                                                  @NotNull PsiElement place,
                                                  @Nullable String symbolName,
                                                  @Nullable String containerName) {
    ErrorWithFixes error = null;
    for (JavaModuleSystem moduleSystem : JavaModuleSystem.EP_NAME.getExtensions()) {
      if (moduleSystem instanceof JavaModuleSystemEx) {
        error = checkAccess((JavaModuleSystemEx)moduleSystem, target, place);
      }
      else if (!isAccessible(moduleSystem, target, place)) {
        String message = JavaErrorBundle.message("visibility.module.access.problem", symbolName, containerName, moduleSystem.getName());
        error = new ErrorWithFixes(message);
      }
      if (error != null) {
        return error;
      }
    }

    return null;
  }

  private static ErrorWithFixes checkAccess(@NotNull JavaModuleSystemEx system, @NotNull PsiElement target, @NotNull PsiElement place) {
    if (target instanceof PsiClass) return system.checkAccess((PsiClass)target, place);
    if (target instanceof PsiPackage) return system.checkAccess(((PsiPackage)target).getQualifiedName(), null, place);
    return null;
  }

  private static boolean isAccessible(@NotNull JavaModuleSystem system, @NotNull PsiElement target, @NotNull PsiElement place) {
    if (target instanceof PsiClass) return system.isAccessible((PsiClass)target, place);
    if (target instanceof PsiPackage) return system.isAccessible(((PsiPackage)target).getQualifiedName(), null, place);
    return true;
  }

  private static PsiElement getContainer(@NotNull PsiModifierListOwner refElement) {
    for (ContainerProvider provider : ContainerProvider.EP_NAME.getExtensions()) {
      PsiElement container = provider.getContainer(refElement);
      if (container != null) return container;
    }
    return refElement.getParent();
  }

  private static String getContainerName(@NotNull PsiModifierListOwner refElement, @NotNull PsiSubstitutor substitutor) {
    PsiElement container = getContainer(refElement);
    return container == null ? "?" : HighlightMessageUtil.getSymbolName(container, substitutor);
  }

  static HighlightInfo.Builder checkValidArrayAccessExpression(@NotNull PsiArrayAccessExpression arrayAccessExpression) {
    PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
    PsiType arrayExpressionType = arrayExpression.getType();

    if (arrayExpressionType != null && !(arrayExpressionType instanceof PsiArrayType)) {
      String description = JavaErrorBundle.message("array.type.expected", JavaHighlightUtil.formatType(arrayExpressionType));
      IntentionAction action = getFixFactory().createReplaceWithListAccessFix(arrayAccessExpression);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(arrayExpression).descriptionAndTooltip(description)
        .registerFix(action, List.of(), null, null, null);
    }

    PsiExpression indexExpression = arrayAccessExpression.getIndexExpression();
    return indexExpression == null ? null : checkAssignability(PsiTypes.intType(), indexExpression.getType(), indexExpression, indexExpression);
  }


  static HighlightInfo.Builder checkCatchParameterIsThrowable(@NotNull PsiParameter parameter) {
    if (parameter.getDeclarationScope() instanceof PsiCatchSection) {
      PsiType type = parameter.getType();
      return checkMustBeThrowable(type, parameter, true);
    }
    return null;
  }

  static HighlightInfo.Builder checkTryResourceIsAutoCloseable(@NotNull PsiResourceListElement resource) {
    PsiType type = resource.getType();
    if (type == null) return null;

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(resource.getProject());
    PsiClassType autoCloseable = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, resource.getResolveScope());
    if (TypeConversionUtil.isAssignable(autoCloseable, type)) return null;

    return createIncompatibleTypeHighlightInfo(autoCloseable, type, resource.getTextRange(), 0);
  }

  static HighlightInfo.Builder checkResourceVariableIsFinal(@NotNull PsiResourceExpression resource) {
    PsiExpression expression = resource.getExpression();

    if (expression instanceof PsiThisExpression) return null;

    if (expression instanceof PsiReferenceExpression ref) {
      PsiElement target = ref.resolve();
      if (target == null) return null;

      if (target instanceof PsiVariable variable) {
        PsiModifierList modifierList = variable.getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL)) return null;

        if (!(variable instanceof PsiField) && HighlightControlFlowUtil.isEffectivelyFinal(variable, resource, ref)) {
          return null;
        }
      }

      String text = JavaErrorBundle.message("resource.variable.must.be.final");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(text);
    }

    String text = JavaErrorBundle.message("declaration.or.variable.expected");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(text);
  }

  static void checkArrayInitializer(@NotNull PsiExpression initializer, @Nullable PsiType type, @NotNull HighlightInfoHolder holder) {
    if (!(initializer instanceof PsiArrayInitializerExpression arrayInitializer)) return;
    if (!(type instanceof PsiArrayType arrayType)) return;

    PsiType componentType = arrayType.getComponentType();

    boolean arrayTypeFixChecked = false;
    VariableArrayTypeFix fix = null;

    PsiExpression[] initializers = arrayInitializer.getInitializers();
    for (PsiExpression expression : initializers) {
      HighlightInfo.Builder info = checkArrayInitializerCompatibleTypes(expression, componentType);
      if (info != null) {

        if (!arrayTypeFixChecked) {
          PsiType checkResult = JavaHighlightUtil.sameType(initializers);
          fix = checkResult != null ? VariableArrayTypeFix.createFix(arrayInitializer, checkResult) : null;
          arrayTypeFixChecked = true;
        }
        if (fix != null) {
          info.registerFix(fix, null, null, null, null);
        }
        holder.add(info.create());
      }
    }
  }

  private static HighlightInfo.Builder checkArrayInitializerCompatibleTypes(@NotNull PsiExpression initializer, @NotNull PsiType componentType) {
    PsiType initializerType = initializer.getType();
    if (initializerType == null) {
      String description = JavaErrorBundle.message("illegal.initializer", JavaHighlightUtil.formatType(componentType));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(initializer).descriptionAndTooltip(description);
    }
    PsiExpression expression = initializer instanceof PsiArrayInitializerExpression ? null : initializer;
    return checkAssignability(componentType, initializerType, expression, initializer);
  }

  @Nullable
  static HighlightInfo.Builder checkPatternVariableRequired(@NotNull PsiReferenceExpression expression,
                                                    @NotNull JavaResolveResult resultForIncompleteCode) {
    if (!(expression.getParent() instanceof PsiCaseLabelElementList)) return null;
    PsiClass resolved = tryCast(resultForIncompleteCode.getElement(), PsiClass.class);
    if (resolved == null) return null;
    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
      .descriptionAndTooltip(JavaErrorBundle.message("type.pattern.expected"));
    String patternVarName = new VariableNameGenerator(expression, VariableKind.LOCAL_VARIABLE).byName("ignored").generate(true);
    IntentionAction action = getFixFactory().createReplaceWithTypePatternFix(expression, resolved, patternVarName);
    info.registerFix(action, null, null, null, null);
    return info;
  }

  static HighlightInfo.Builder checkExpressionRequired(@NotNull PsiReferenceExpression expression,
                                               @NotNull JavaResolveResult resultForIncompleteCode, @NotNull PsiFile containingFile) {
    if (expression.getNextSibling() instanceof PsiErrorElement) return null;

    PsiElement resolved = resultForIncompleteCode.getElement();
    if (resolved == null || resolved instanceof PsiVariable) return null;

    PsiElement parent = expression.getParent();
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiMethodCallExpression || parent instanceof PsiBreakStatement) {
      return null;
    }

    String description = JavaErrorBundle.message("expression.expected");
    HighlightInfo.Builder info =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
    UnresolvedReferenceQuickFixUpdater.getInstance(containingFile.getProject()).registerQuickFixesLater(expression, info);
    return info;
  }

  static HighlightInfo.Builder checkArrayInitializerApplicable(@NotNull PsiArrayInitializerExpression expression) {
    /*
    JLS 10.6 Array Initializers
    An array initializer may be specified in a declaration, or as part of an array creation expression
    */
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiVariable variable) {
      PsiTypeElement typeElement = variable.getTypeElement();
      boolean isInferredType = typeElement != null && typeElement.isInferredType();
      if (!isInferredType && variable.getType() instanceof PsiArrayType) return null;
    }
    else if (parent instanceof PsiNewExpression || parent instanceof PsiArrayInitializerExpression) {
      return null;
    }

    String description = JavaErrorBundle.message("array.initializer.not.allowed");
    HighlightInfo.Builder info =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
    IntentionAction action = getFixFactory().createAddNewArrayExpressionFix(expression);
    info.registerFix(action, null, null, null, null);
    return info;
  }


  static HighlightInfo.Builder checkCaseStatement(@NotNull PsiSwitchLabelStatementBase statement) {
    PsiSwitchBlock switchBlock = statement.getEnclosingSwitchBlock();
    if (switchBlock == null) {
      String description = JavaErrorBundle.message("case.statement.outside.switch");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description);
    }

    return null;
  }

  static void checkSwitchExpressionHasResult(@NotNull PsiSwitchExpression switchExpression,
                                             @NotNull HighlightInfoHolder holder) {
    PsiCodeBlock switchBody = switchExpression.getBody();
    if (switchBody != null) {
      PsiStatement lastStatement = PsiTreeUtil.getPrevSiblingOfType(switchBody.getRBrace(), PsiStatement.class);
      boolean hasResult = false;
      if (lastStatement instanceof PsiSwitchLabeledRuleStatement) {
        for (PsiSwitchLabeledRuleStatement rule = (PsiSwitchLabeledRuleStatement)lastStatement;
             rule != null;
             rule = PsiTreeUtil.getPrevSiblingOfType(rule, PsiSwitchLabeledRuleStatement.class)) {
          PsiStatement ruleBody = rule.getBody();
          if (ruleBody instanceof PsiExpressionStatement) {
            hasResult = true;
          }
          // the expression and throw statements are fine, only the block statement could be an issue
          // 15.28.1 If the switch block consists of switch rules, then any switch rule block cannot complete normally
          if (ruleBody instanceof PsiBlockStatement) {
            if (ControlFlowUtils.statementMayCompleteNormally(ruleBody)) {
              PsiElement target = ObjectUtils.notNull(tryCast(rule.getFirstChild(), PsiKeyword.class), rule);
              String message = JavaErrorBundle.message("switch.expr.rule.should.produce.result");
              holder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(target).descriptionAndTooltip(message).create());
            }
            else if (!hasResult && hasYield(switchExpression, ruleBody)) {
              hasResult = true;
            }
          }
        }
        if (holder.hasErrorResults()) {
          return;
        }
      }
      else {
        // previous statements may have no result as well, but in that case they fall through to the last one, which needs to be checked anyway
        if (lastStatement != null && ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
          PsiElement target =
            ObjectUtils.notNull(tryCast(switchExpression.getFirstChild(), PsiKeyword.class), switchExpression);
          String message = JavaErrorBundle.message("switch.expr.should.produce.result");
          holder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(target).descriptionAndTooltip(message).create());
          return;
        }
        hasResult = hasYield(switchExpression, switchBody);
      }
      if (!hasResult) {
        PsiElement target = ObjectUtils.notNull(tryCast(switchExpression.getFirstChild(), PsiKeyword.class), switchExpression);
        holder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(target)
                                           .descriptionAndTooltip(JavaErrorBundle.message("switch.expr.no.result")).create());
      }
    }
  }

  private static boolean hasYield(@NotNull PsiSwitchExpression switchExpression, @NotNull PsiElement scope) {
    class YieldFinder extends JavaRecursiveElementWalkingVisitor {
      private boolean hasYield;

      @Override
      public void visitYieldStatement(@NotNull PsiYieldStatement statement) {
        if (statement.findEnclosingExpression() == switchExpression) {
          hasYield = true;
          stopWalking();
        }
      }

      // do not go inside to save time: declarations cannot contain yield that points to outer switch expression
      @Override
      public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {}

      // do not go inside to save time: expressions cannot contain yield that points to outer switch expression
      @Override
      public void visitExpression(@NotNull PsiExpression expression) {}
    }
    YieldFinder finder = new YieldFinder();
    scope.accept(finder);
    return finder.hasYield;
  }

  /**
   * See JLS 8.3.3.
   */
  static HighlightInfo.Builder checkIllegalForwardReferenceToField(@NotNull PsiReferenceExpression expression, @NotNull PsiField referencedField) {
    Boolean isIllegalForwardReference = isIllegalForwardReferenceToField(expression, referencedField, false);
    if (isIllegalForwardReference == null) return null;
    String description = JavaErrorBundle.message(isIllegalForwardReference ? "illegal.forward.reference" : "illegal.self.reference");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
  }

  public static Boolean isIllegalForwardReferenceToField(@NotNull PsiReferenceExpression expression,
                                                         @NotNull PsiField referencedField,
                                                         boolean acceptQualified) {
    PsiClass containingClass = referencedField.getContainingClass();
    if (containingClass == null) return null;
    if (expression.getContainingFile() != referencedField.getContainingFile()) return null;
    TextRange fieldRange = referencedField.getTextRange();
    if (fieldRange == null || expression.getTextRange().getStartOffset() >= fieldRange.getEndOffset()) return null;
    if (!acceptQualified) {
      if (containingClass.isEnum()) {
        if (isLegalForwardReferenceInEnum(expression, referencedField, containingClass)) return null;
      }
      // simple reference can be illegal (JLS 8.3.3)
      else if (expression.getQualifierExpression() != null) return null;
    }
    PsiField initField = findEnclosingFieldInitializer(expression);
    PsiClassInitializer classInitializer = findParentClassInitializer(expression);
    if (initField == null && classInitializer == null) return null;
    // instance initializers may access static fields
    boolean isStaticClassInitializer = classInitializer != null && classInitializer.hasModifierProperty(PsiModifier.STATIC);
    boolean isStaticInitField = initField != null && initField.hasModifierProperty(PsiModifier.STATIC);
    boolean inStaticContext = isStaticInitField || isStaticClassInitializer;
    if (!inStaticContext && referencedField.hasModifierProperty(PsiModifier.STATIC)) return null;
    if (PsiUtil.isOnAssignmentLeftHand(expression) && !PsiUtil.isAccessedForReading(expression)) return null;
    if (!containingClass.getManager().areElementsEquivalent(containingClass, PsiTreeUtil.getParentOfType(expression, PsiClass.class))) {
      return null;
    }
    return initField != referencedField;
  }

  private static boolean isLegalForwardReferenceInEnum(@NotNull PsiReferenceExpression expression,
                                                       @NotNull PsiField referencedField,
                                                       @NotNull PsiClass containingClass) {
    PsiExpression qualifierExpr = expression.getQualifierExpression();
    // simple reference can be illegal (JLS 8.3.3)
    if (qualifierExpr == null) return false;
    if (!(qualifierExpr instanceof PsiReferenceExpression)) return true;

    PsiElement qualifiedReference = ((PsiReferenceExpression)qualifierExpr).resolve();
    if (containingClass.equals(qualifiedReference)) {
      // static fields that are constant variables (4.12.4) are initialized before other static fields (12.4.2),
      // so a qualified reference to the constant variable is possible.
      return PsiUtil.isCompileTimeConstant(referencedField);
    }
    return true;
  }

  /**
   * @return field that has initializer with this element as subexpression or null if not found
   */
  static PsiField findEnclosingFieldInitializer(@NotNull PsiElement entry) {
    PsiElement element = entry;
    while (element != null) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiField field) {
        if (element == field.getInitializer()) return field;
        if (field instanceof PsiEnumConstant enumConstant && element == enumConstant.getArgumentList()) return field;
      }
      if (element instanceof PsiClass || element instanceof PsiMethod) return null;
      element = parent;
    }
    return null;
  }

  private static PsiClassInitializer findParentClassInitializer(@NotNull PsiElement root) {
    PsiElement element = root;
    while (element != null) {
      if (element instanceof PsiClassInitializer) return (PsiClassInitializer)element;
      if (element instanceof PsiClass || element instanceof PsiMethod) return null;
      element = element.getParent();
    }
    return null;
  }


  static HighlightInfo.Builder checkIllegalType(@NotNull PsiTypeElement typeElement, @NotNull PsiFile containingFile) {
    PsiElement parent = typeElement.getParent();
    if (parent instanceof PsiTypeElement) return null;

    if (PsiUtil.isInsideJavadocComment(typeElement)) return null;

    PsiType type = typeElement.getType();
    PsiType componentType = type.getDeepComponentType();
    if (componentType instanceof PsiClassType) {
      PsiClass aClass = PsiUtil.resolveClassInType(componentType);
      if (aClass == null) {
        if (typeElement.isInferredType() && parent instanceof PsiLocalVariable) {
          PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(((PsiLocalVariable)parent).getInitializer());
          if (initializer instanceof PsiNewExpression) {
            // The problem is already reported on the initializer
            return null;
          }
        }
        String canonicalText = componentType.getCanonicalText();
        String description = JavaErrorBundle.message("unknown.class", canonicalText);
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description);
        PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
        if (referenceElement != null) {
          UnresolvedReferenceQuickFixUpdater.getInstance(containingFile.getProject()).registerQuickFixesLater(referenceElement, info);
        }
        return info;
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkIllegalVoidType(@NotNull PsiKeyword type) {
    if (!PsiKeyword.VOID.equals(type.getText())) return null;

    PsiElement parent = type.getParent();
    if (parent instanceof PsiErrorElement) return null;
    if (parent instanceof PsiTypeElement) {
      PsiElement typeOwner = parent.getParent();
      if (typeOwner != null) {
        // do not highlight incomplete declarations
        if (PsiUtilCore.hasErrorElementChild(typeOwner)) return null;
      }

      if (typeOwner instanceof PsiMethod method) {
        if (method.getReturnTypeElement() == parent && PsiTypes.voidType().equals(method.getReturnType())) return null;
      }
      else if (typeOwner instanceof PsiClassObjectAccessExpression classAccess) {
        if (TypeConversionUtil.isVoidType(classAccess.getOperand().getType())) return null;
      }
      else if (typeOwner instanceof JavaCodeFragment) {
        if (typeOwner.getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null) return null;
      }
    }

    String description = JavaErrorBundle.message("illegal.type.void");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(type).descriptionAndTooltip(description);
  }

  static HighlightInfo.Builder checkMemberReferencedBeforeConstructorCalled(@NotNull PsiElement expression,
                                                                    @Nullable PsiElement resolved,
                                                                    @NotNull PsiFile containingFile,
                                                                    @NotNull Function<? super PsiElement, ? extends PsiClass> insideConstructorOfClass) {
    if (insideConstructorOfClass.apply(expression) == null) {
      // not inside expression inside constructor
      return null;
    }

    PsiClass referencedClass;
    String resolvedName;
    PsiType type;
    if (expression instanceof PsiJavaCodeReferenceElement) {
      // redirected ctr
      if (PsiKeyword.THIS.equals(((PsiJavaCodeReferenceElement)expression).getReferenceName())
          && resolved instanceof PsiMethod
          && ((PsiMethod)resolved).isConstructor()) {
        return null;
      }
      PsiElement qualifier = ((PsiJavaCodeReferenceElement)expression).getQualifier();
      type = qualifier instanceof PsiExpression ? ((PsiExpression)qualifier).getType() : null;
      referencedClass = PsiUtil.resolveClassInType(type);

      boolean isSuperCall = JavaPsiConstructorUtil.isSuperConstructorCall(expression.getParent());
      if (resolved == null && isSuperCall) {
        if (qualifier instanceof PsiReferenceExpression) {
          resolved = ((PsiReferenceExpression)qualifier).resolve();
          expression = qualifier;
          type = ((PsiReferenceExpression)qualifier).getType();
          referencedClass = PsiUtil.resolveClassInType(type);
        }
        else if (qualifier == null) {
          resolved = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiMember.class);
          if (resolved != null) {
            referencedClass = ((PsiMethod)resolved).getContainingClass();
          }
        }
        else if (qualifier instanceof PsiThisExpression) {
          referencedClass = PsiUtil.resolveClassInType(((PsiThisExpression)qualifier).getType());
        }
      }
      if (resolved instanceof PsiField referencedField) {
        if (referencedField.hasModifierProperty(PsiModifier.STATIC)) return null;
        resolvedName = PsiFormatUtil
          .formatVariable(referencedField, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
        referencedClass = referencedField.getContainingClass();
      }
      else if (resolved instanceof PsiMethod method) {
        if (method.hasModifierProperty(PsiModifier.STATIC)) return null;
        PsiElement nameElement =
          expression instanceof PsiThisExpression ? expression : ((PsiJavaCodeReferenceElement)expression).getReferenceNameElement();
        String name = nameElement == null ? null : nameElement.getText();
        if (isSuperCall) {
          if (referencedClass == null) return null;
          if (qualifier == null) {
            PsiClass superClass = referencedClass.getSuperClass();
            if (superClass != null
                && PsiUtil.isInnerClass(superClass)
                && InheritanceUtil.isInheritorOrSelf(referencedClass, superClass.getContainingClass(), true)) {
              // by default super() is considered "this"-qualified
              resolvedName = PsiKeyword.THIS;
            }
            else {
              return null;
            }
          }
          else {
            resolvedName = qualifier.getText();
          }
        }
        else if (PsiKeyword.THIS.equals(name)) {
          resolvedName = PsiKeyword.THIS;
        }
        else {
          resolvedName = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                                                  PsiFormatUtilBase.SHOW_NAME, 0);
          if (referencedClass == null) referencedClass = method.getContainingClass();
        }
      }
      else if (resolved instanceof PsiClass aClass) {
        if (aClass.hasModifierProperty(PsiModifier.STATIC)) return null;
        referencedClass = aClass.getContainingClass();
        if (referencedClass == null) return null;
        resolvedName = PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME);
      }
      else {
        return null;
      }
    }
    else if (expression instanceof PsiThisExpression thisExpression) {
      type = thisExpression.getType();
      referencedClass = PsiUtil.resolveClassInType(type);
      if (thisExpression.getQualifier() != null) {
        resolvedName = referencedClass == null
                       ? null
                       : PsiFormatUtil.formatClass(referencedClass, PsiFormatUtilBase.SHOW_NAME) + "." + PsiKeyword.THIS;
      }
      else {
        resolvedName = PsiKeyword.THIS;
      }
    }
    else {
      return null;
    }

    if (referencedClass == null ||
        PsiTreeUtil.getParentOfType(expression, PsiReferenceParameterList.class, true, PsiExpression.class) != null) {
      return null;
    }

    PsiElement element = expression.getParent();
    while (element != null) {
      // check if expression inside super()/this() call

      if (JavaPsiConstructorUtil.isConstructorCall(element)) {
        PsiClass parentClass = insideConstructorOfClass.apply(element);
        if (parentClass == null) {
          return null;
        }

        // only this class/superclasses instance methods are not allowed to call
        if (PsiUtil.isInnerClass(parentClass) && referencedClass == parentClass.getContainingClass()) return null;
        // field or method should be declared in this class or super
        if (!InheritanceUtil.isInheritorOrSelf(parentClass, referencedClass, true)) return null;
        // and point to our instance
        if (expression instanceof PsiReferenceExpression &&
            !isThisOrSuperReference(((PsiReferenceExpression)expression).getQualifierExpression(), parentClass)) {
          return null;
        }

        if (expression instanceof PsiJavaCodeReferenceElement &&
            !parentClass.equals(PsiTreeUtil.getParentOfType(expression, PsiClass.class)) &&
            PsiTreeUtil.getParentOfType(expression, PsiTypeElement.class) != null) {
          return null;
        }

        if (expression instanceof PsiJavaCodeReferenceElement &&
            PsiTreeUtil.getParentOfType(expression, PsiClassObjectAccessExpression.class) != null) {
          return null;
        }

        HighlightInfo.Builder builder = createMemberReferencedError(resolvedName, expression.getTextRange());
        if (expression instanceof PsiReferenceExpression && PsiUtil.isInnerClass(parentClass)) {
          String referenceName = ((PsiReferenceExpression)expression).getReferenceName();
          PsiClass containingClass = parentClass.getContainingClass();
          LOG.assertTrue(containingClass != null);
          PsiField fieldInContainingClass = containingClass.findFieldByName(referenceName, true);
          if (fieldInContainingClass != null && ((PsiReferenceExpression)expression).getQualifierExpression() == null) {
            builder.registerFix(new QualifyWithThisFix(containingClass, expression), null, null, null, null);
          }
        }

        return builder;
      }

      if (element instanceof PsiReferenceExpression ref) {
        PsiElement resolve;
        if (element instanceof PsiReferenceExpressionImpl referenceExpression) {
          JavaResolveResult[] results = JavaResolveUtil
            .resolveWithContainingFile(referenceExpression, PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE, true, false,
                                       containingFile);
          resolve = results.length == 1 ? results[0].getElement() : null;
        }
        else {
          resolve = ref.resolve();
        }
        if (resolve instanceof PsiField field && field.hasModifierProperty(PsiModifier.STATIC)) {
          return null;
        }
      }

      element = element.getParent();
      if (element instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass)element, referencedClass, true)) return null;
    }
    return null;
  }

  @NotNull
  private static HighlightInfo.Builder createMemberReferencedError(@NotNull String resolvedName, @NotNull TextRange textRange) {
    String description = JavaErrorBundle.message("member.referenced.before.constructor.called", resolvedName);
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
  }

  static HighlightInfo.Builder checkImplicitThisReferenceBeforeSuper(@NotNull PsiClass aClass, @NotNull JavaSdkVersion javaSdkVersion) {
    if (javaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7)) return null;
    if (aClass instanceof PsiAnonymousClass || aClass instanceof PsiTypeParameter) return null;
    PsiClass superClass = aClass.getSuperClass();
    if (superClass == null || !PsiUtil.isInnerClass(superClass)) return null;
    PsiClass outerClass = superClass.getContainingClass();
    if (!InheritanceUtil.isInheritorOrSelf(aClass, outerClass, true)) {
      return null;
    }
    // 'this' can be used as an (implicit) super() qualifier
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      TextRange range = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return createMemberReferencedError(aClass.getName() + ".this", range);
    }
    for (PsiMethod constructor : constructors) {
      PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
      if (!JavaPsiConstructorUtil.isSuperConstructorCall(call)) {
        return createMemberReferencedError(aClass.getName() + ".this", HighlightNamesUtil.getMethodDeclarationTextRange(constructor));
      }
    }
    return null;
  }

  private static boolean isThisOrSuperReference(@Nullable PsiExpression qualifierExpression, @NotNull PsiClass aClass) {
    if (qualifierExpression == null) return true;
    if (!(qualifierExpression instanceof PsiQualifiedExpression expression)) return false;
    PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
    if (qualifier == null) return true;
    PsiElement resolved = qualifier.resolve();
    return resolved instanceof PsiClass && InheritanceUtil.isInheritorOrSelf(aClass, (PsiClass)resolved, true);
  }


  static HighlightInfo.Builder checkLabelWithoutStatement(@NotNull PsiLabeledStatement statement) {
    if (statement.getStatement() == null) {
      String description = JavaErrorBundle.message("label.without.statement");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description);
    }
    return null;
  }


  static HighlightInfo.Builder checkLabelAlreadyInUse(@NotNull PsiLabeledStatement statement) {
    PsiIdentifier identifier = statement.getLabelIdentifier();
    String text = identifier.getText();
    PsiElement element = statement;
    while (element != null) {
      if (element instanceof PsiMethod || element instanceof PsiClass) break;
      if (element instanceof PsiLabeledStatement && element != statement &&
          Objects.equals(((PsiLabeledStatement)element).getLabelIdentifier().getText(), text)) {
        String description = JavaErrorBundle.message("duplicate.label", text);
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(description);
      }
      element = element.getParent();
    }
    return null;
  }


  static HighlightInfo.Builder checkUnclosedComment(@NotNull PsiComment comment) {
    if (!(comment instanceof PsiDocComment) && comment.getTokenType() != JavaTokenType.C_STYLE_COMMENT) return null;
    if (!comment.getText().endsWith("*/")) {
      int start = comment.getTextRange().getEndOffset() - 1;
      int end = start + 1;
      String description = JavaErrorBundle.message("unclosed.comment");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(start, end).descriptionAndTooltip(description);
    }
    return null;
  }

  static void checkIllegalUnicodeEscapes(@NotNull PsiElement element, @NotNull HighlightInfoHolder holder) {
    parseUnicodeEscapes(element.getText(), (start, end) -> {
      int offset = element.getTextOffset();
      holder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(offset + start, offset + end)
        .descriptionAndTooltip(JavaErrorBundle.message("illegal.unicode.escape")).create());
    });
  }

  private static @NotNull String parseUnicodeEscapes(@NotNull String text, @Nullable BiConsumer<? super Integer, ? super Integer> illegalEscapeConsumer) {
    // JLS 3.3
    if (!text.contains("\\u")) return text;
    StringBuilder result = new StringBuilder();
    boolean escape = false;
    for (int i = 0, length = text.length(); i < length; i++) {
      char c = text.charAt(i);
      if (c == '\\') {
        if (escape) result.append("\\\\");
        escape = !escape;
      }
      else {
        if (!escape) {
          result.append(c);
        }
        else if (c != 'u') {
          result.append('\'').append(c);
          escape = false;
        }
        else {
          int startOfUnicodeEscape = i - 1;
          do {
            i++;
            if (i == length) {
              if (illegalEscapeConsumer != null) illegalEscapeConsumer.accept(startOfUnicodeEscape, i);
              return result.toString();
            }
            c = text.charAt(i);
          } while (c == 'u');
          int value = 0;
          for (int j = 0; j < 4; j++) {
            if (i + j >= length) {
              if (illegalEscapeConsumer != null) illegalEscapeConsumer.accept(startOfUnicodeEscape, i + j);
              return result.toString();
            }
            value <<= 4;
            c = text.charAt(i + j);
            if ('0' <= c && c <= '9') value += c - '0';
            else if ('a' <= c && c <= 'f') value += (c - 'a') + 10;
            else if ('A' <= c && c <= 'F') value += (c - 'A') + 10;
            else {
              if (illegalEscapeConsumer != null) illegalEscapeConsumer.accept(startOfUnicodeEscape, i + j);
              value = -1;
              break;
            }
          }
          if (value != -1) {
            i += 3;
            result.appendCodePoint(value);
          }
          escape = false;
        }
      }
    }
    return result.toString();
  }

  static void checkCatchTypeIsDisjoint(@NotNull PsiParameter parameter, @NotNull HighlightInfoHolder holder) {
    if (!(parameter.getType() instanceof PsiDisjunctionType)) return;

    List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    for (int i = 0, size = typeElements.size(); i < size; i++) {
      PsiClass class1 = PsiUtil.resolveClassInClassTypeOnly(typeElements.get(i).getType());
      if (class1 == null) continue;
      for (int j = i + 1; j < size; j++) {
        PsiClass class2 = PsiUtil.resolveClassInClassTypeOnly(typeElements.get(j).getType());
        if (class2 == null) continue;
        boolean sub = InheritanceUtil.isInheritorOrSelf(class1, class2, true);
        boolean sup = InheritanceUtil.isInheritorOrSelf(class2, class1, true);
        if (sub || sup) {
          String name1 = PsiFormatUtil.formatClass(class1, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
          String name2 = PsiFormatUtil.formatClass(class2, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
          String message = JavaErrorBundle.message("exception.must.be.disjoint", sub ? name1 : name2, sub ? name2 : name1);
          PsiTypeElement element = typeElements.get(sub ? i : j);
          HighlightInfo.Builder builder =
            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(message);
          IntentionAction action = getFixFactory().createDeleteMultiCatchFix(element);
          builder.registerFix(action, null, null, null, null);
          holder.add(builder.create());
          break;
        }
      }
    }
  }


  static void checkExceptionAlreadyCaught(@NotNull PsiParameter parameter, @NotNull HighlightInfoHolder holder) {
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiCatchSection catchSection)) return;

    PsiCatchSection[] allCatchSections = catchSection.getTryStatement().getCatchSections();
    int startFrom = ArrayUtilRt.find(allCatchSections, catchSection) - 1;
    if (startFrom < 0) return;

    List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    boolean isInMultiCatch = typeElements.size() > 1;

    for (PsiTypeElement typeElement : typeElements) {
      PsiClass catchClass = PsiUtil.resolveClassInClassTypeOnly(typeElement.getType());
      if (catchClass == null) continue;

      for (int i = startFrom; i >= 0; i--) {
        PsiCatchSection upperCatchSection = allCatchSections[i];
        PsiType upperCatchType = upperCatchSection.getCatchType();

        boolean highlight = upperCatchType instanceof PsiDisjunctionType
                                  ? checkMultipleTypes(catchClass, ((PsiDisjunctionType)upperCatchType).getDisjunctions())
                                  : checkSingleType(catchClass, upperCatchType);
        if (highlight) {
          String className = PsiFormatUtil.formatClass(catchClass, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
          String description = JavaErrorBundle.message("exception.already.caught", className);
          HighlightInfo.Builder builder =
            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description);

          IntentionAction action;
          if (isInMultiCatch) {
            action = getFixFactory().createDeleteMultiCatchFix(typeElement);
          }
          else {
            action = getFixFactory().createDeleteCatchFix(parameter);
          }
          builder.registerFix(action, null, null, null, null);
          builder.registerFix(getFixFactory().createMoveCatchUpFix(catchSection, upperCatchSection), null, null, null, null);
          holder.add(builder.create());
        }
      }
    }
  }

  private static boolean checkMultipleTypes(@NotNull PsiClass catchClass, @NotNull List<? extends PsiType> upperCatchTypes) {
    for (int i = upperCatchTypes.size() - 1; i >= 0; i--) {
      if (checkSingleType(catchClass, upperCatchTypes.get(i))) return true;
    }
    return false;
  }

  private static boolean checkSingleType(@NotNull PsiClass catchClass, @Nullable PsiType upperCatchType) {
    PsiClass upperCatchClass = PsiUtil.resolveClassInType(upperCatchType);
    return upperCatchClass != null && InheritanceUtil.isInheritorOrSelf(catchClass, upperCatchClass, true);
  }


  static HighlightInfo.Builder checkTernaryOperatorConditionIsBoolean(@NotNull PsiExpression expression, @Nullable PsiType type) {
    if (expression.getParent() instanceof PsiConditionalExpression &&
        ((PsiConditionalExpression)expression.getParent()).getCondition() == expression && !TypeConversionUtil.isBooleanType(type)) {
      return createMustBeBooleanInfo(expression, type);
    }
    return null;
  }

  static HighlightInfo.Builder checkAssertOperatorTypes(@NotNull PsiExpression expression, @Nullable PsiType type) {
    if (type == null) return null;
    if (!(expression.getParent() instanceof PsiAssertStatement assertStatement)) {
      return null;
    }
    if (expression == assertStatement.getAssertCondition() && !TypeConversionUtil.isBooleanType(type)) {
      // addTypeCast quickfix is not applicable here since no type can be cast to boolean
      HighlightInfo.Builder builder = createIncompatibleTypeHighlightInfo(PsiTypes.booleanType(), type, expression.getTextRange(), 0);
      if (expression instanceof PsiAssignmentExpression &&
          ((PsiAssignmentExpression)expression).getOperationTokenType() == JavaTokenType.EQ) {
        IntentionAction action = getFixFactory().createAssignmentToComparisonFix((PsiAssignmentExpression)expression);
        builder.registerFix(action, null, null, null, null);
      }
      return builder;
    }
    if (expression == assertStatement.getAssertDescription() && TypeConversionUtil.isVoidType(type)) {
      String description = JavaErrorBundle.message("void.type.is.not.allowed");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
    }
    return null;
  }


  static HighlightInfo.Builder checkSynchronizedExpressionType(@NotNull PsiExpression expression,
                                                       @Nullable PsiType type,
                                                       @NotNull PsiFile containingFile) {
    if (type == null) return null;
    if (expression.getParent() instanceof PsiSynchronizedStatement synchronizedStatement) {
      if (expression == synchronizedStatement.getLockExpression() &&
          (type instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(type))) {
        PsiClassType objectType = PsiType.getJavaLangObject(containingFile.getManager(), expression.getResolveScope());
        return createIncompatibleTypeHighlightInfo(objectType, type, expression.getTextRange(), 0);
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkConditionalExpressionBranchTypesMatch(@NotNull PsiExpression expression, @Nullable PsiType type) {
    PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiConditionalExpression conditionalExpression)) {
      return null;
    }
    // check else branches only
    if (conditionalExpression.getElseExpression() != expression) return null;
    PsiExpression thenExpression = conditionalExpression.getThenExpression();
    assert thenExpression != null;
    PsiType thenType = thenExpression.getType();
    if (thenType == null || type == null) return null;
    if (conditionalExpression.getType() == null) {
      if (PsiUtil.isLanguageLevel8OrHigher(conditionalExpression) && PsiPolyExpressionUtil.isPolyExpression(conditionalExpression)) {
        return null;
      }
      // cannot derive type of conditional expression
      // elseType will never be cast-able to thenType, so no quick fix here
      return createIncompatibleTypeHighlightInfo(thenType, type, expression.getTextRange(), 0);
    }
    return null;
  }

  @NotNull
  static HighlightInfo.Builder createIncompatibleTypeHighlightInfo(@NotNull PsiType lType,
                                                           @Nullable PsiType rType,
                                                           @NotNull TextRange textRange,
                                                           int navigationShift) {
    return createIncompatibleTypeHighlightInfo(lType, rType, textRange, navigationShift, getReasonForIncompatibleTypes(rType));
  }

  @NotNull
  static HighlightInfo.Builder createIncompatibleTypeHighlightInfo(@NotNull PsiType lType,
                                                           @Nullable PsiType rType,
                                                           @NotNull TextRange textRange,
                                                           int navigationShift,
                                                           @NotNull String reason) {
    lType = PsiUtil.convertAnonymousToBaseType(lType);
    rType = rType == null ? null : PsiUtil.convertAnonymousToBaseType(rType);
    String styledReason = reason.isEmpty() ? ""
                                           : String
                            .format("<table><tr><td style=''padding-top: 10px; padding-left: 4px;''>%s</td></tr></table>", reason);
    String toolTip = createIncompatibleTypesTooltip(lType, rType,
                                                    (lRawType, lTypeArguments, rRawType, rTypeArguments) ->
                                                      JavaErrorBundle
                                                        .message("incompatible.types.html.tooltip", lRawType, lTypeArguments, rRawType,
                                                                 rTypeArguments, styledReason,
                                                                 "#" + ColorUtil.toHex(UIUtil.getContextHelpForeground())));
    String description = JavaErrorBundle.message(
      "incompatible.types", JavaHighlightUtil.formatType(lType), JavaHighlightUtil.formatType(rType));
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(textRange)
      .description(description)
      .escapedToolTip(toolTip)
      .navigationShift(navigationShift);
  }

  public static HighlightInfo.Builder checkArrayType(PsiTypeElement type) {
    int dimensions = 0;
    for (PsiElement child = type.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET)) {
        dimensions++;
      }
    }
    if (dimensions > 255) {
      // JVM Specification, 4.3.2: no more than 255 dimensions allowed
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(type.getTextRange())
        .description(JavaErrorBundle.message("too.many.array.dimensions"));
    }
    return null;
  }

  @FunctionalInterface
  interface IncompatibleTypesTooltipComposer {
    @NotNull @NlsContexts.Tooltip
    String consume(@NotNull @NlsSafe String lRawType,
                   @NotNull @NlsSafe String lTypeArguments,
                   @NotNull @NlsSafe String rRawType,
                   @NotNull @NlsSafe String rTypeArguments);

    /**
     * Override if expected/actual pair layout is a row
     */
    default boolean skipTypeArgsColumns() {
      return false;
    }
  }

  @NotNull
  static @NlsContexts.Tooltip String createIncompatibleTypesTooltip(PsiType lType,
                                                                    PsiType rType,
                                                                    @NotNull IncompatibleTypesTooltipComposer consumer) {
    Trinity<PsiType, PsiTypeParameter[], PsiSubstitutor> lTypeData = typeData(lType);
    Trinity<PsiType, PsiTypeParameter[], PsiSubstitutor> rTypeData = typeData(rType);
    PsiTypeParameter[] lTypeParams = lTypeData.second;
    PsiTypeParameter[] rTypeParams = rTypeData.second;

    int typeParamColumns = Math.max(lTypeParams.length, rTypeParams.length);
    boolean skipColumns = consumer.skipTypeArgsColumns();
    StringBuilder requiredRow = new StringBuilder();
    StringBuilder foundRow = new StringBuilder();
    for (int i = 0; i < typeParamColumns; i++) {
      PsiTypeParameter lTypeParameter = i >= lTypeParams.length ? null : lTypeParams[i];
      PsiTypeParameter rTypeParameter = i >= rTypeParams.length ? null : rTypeParams[i];
      PsiType lSubstitutedType = lTypeParameter == null ? null : lTypeData.third.substitute(lTypeParameter);
      PsiType rSubstitutedType = rTypeParameter == null ? null : rTypeData.third.substitute(rTypeParameter);
      boolean matches = lSubstitutedType == rSubstitutedType ||
                        lSubstitutedType != null &&
                        rSubstitutedType != null &&
                        TypeConversionUtil.typesAgree(lSubstitutedType, rSubstitutedType, true);
      String openBrace = i == 0 ? "&lt;" : "";
      String closeBrace = i == typeParamColumns - 1 ? "&gt;" : ",";
      boolean showShortType = showShortType(lSubstitutedType, rSubstitutedType);

      requiredRow.append(skipColumns ? ""
                                     : "<td style='padding: 0px 0px 8px 0px;'>")
        .append(lTypeParams.length == 0 ? "" : openBrace)
        .append(redIfNotMatch(lSubstitutedType, true, showShortType))
        .append(i < lTypeParams.length ? closeBrace : "")
        .append(skipColumns ? ""
                            : "</td>");

      foundRow.append(skipColumns ? ""
                                  : "<td style='padding: 0px 0px 0px 0px;'>")
        .append(rTypeParams.length == 0 ? "" : openBrace)
        .append(redIfNotMatch(rSubstitutedType, matches, showShortType))
        .append(i < rTypeParams.length ? closeBrace : "")
        .append(skipColumns ? ""
                            : "</td>");
    }
    PsiType lRawType = lType instanceof PsiClassType ? ((PsiClassType)lType).rawType() : lType;
    PsiType rRawType = rType instanceof PsiClassType ? ((PsiClassType)rType).rawType() : rType;
    boolean assignable = lRawType == null || rRawType == null || TypeConversionUtil.isAssignable(lRawType, rRawType);
    boolean shortType = showShortType(lRawType, rRawType);
    return consumer.consume(redIfNotMatch(lRawType, true, shortType).toString(),
                            requiredRow.toString(),
                            redIfNotMatch(rRawType, assignable, shortType).toString(),
                            foundRow.toString());
  }

  static boolean showShortType(@Nullable PsiType lType, @Nullable PsiType rType) {
    if (Comparing.equal(lType, rType)) return true;

    return lType != null && rType != null && !Comparing.strEqual(lType.getPresentableText(), rType.getPresentableText());
  }

  private static @NotNull String getReasonForIncompatibleTypes(PsiType rType) {
    if (rType instanceof PsiMethodReferenceType) {
      JavaResolveResult[] results = ((PsiMethodReferenceType)rType).getExpression().multiResolve(false);
      if (results.length > 1) {
        PsiElement element1 = results[0].getElement();
        PsiElement element2 = results[1].getElement();
        if (element1 instanceof PsiMethod && element2 instanceof PsiMethod) {
          String candidate1 = format(element1);
          String candidate2 = format(element2);
          return JavaErrorBundle.message("incompatible.types.reason.ambiguous.method.reference", candidate1, candidate2);
        }
      }
    }
    return "";
  }

  @NotNull
  private static Trinity<PsiType, PsiTypeParameter[], PsiSubstitutor> typeData(PsiType type) {
    PsiTypeParameter[] parameters = PsiTypeParameter.EMPTY_ARRAY;
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      substitutor = resolveResult.getSubstitutor();
      PsiClass psiClass = resolveResult.getElement();
      parameters = psiClass == null || ((PsiClassType)type).isRaw() ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
    }
    return Trinity.create(type, parameters, substitutor);
  }

  @NotNull
  static @NlsSafe HtmlChunk redIfNotMatch(@Nullable PsiType type, boolean matches, boolean shortType) {
    if (type == null) return HtmlChunk.empty();
    Color color;
    if (matches) {
      color = ExperimentalUI.isNewUI() ? JBUI.CurrentTheme.Editor.Tooltip.FOREGROUND : UIUtil.getToolTipForeground();
    }
    else {
      color = NamedColorUtil.getErrorForeground();
    }
    return HtmlChunk.tag("font").attr("color", ColorUtil.toHtmlColor(color))
      .addText(shortType || type instanceof PsiCapturedWildcardType ? type.getPresentableText() : type.getCanonicalText());
  }


  static HighlightInfo.Builder checkSingleImportClassConflict(@NotNull PsiImportStatement statement,
                                                      @NotNull Map<String, Pair<PsiImportStaticReferenceElement, PsiClass>> importedClasses,
                                                      @NotNull PsiFile containingFile) {
    if (statement.isOnDemand()) return null;
    PsiElement element = statement.resolve();
    if (element instanceof PsiClass) {
      String name = ((PsiClass)element).getName();
      Pair<PsiImportStaticReferenceElement, PsiClass> imported = importedClasses.get(name);
      PsiClass importedClass = Pair.getSecond(imported);
      if (importedClass != null && !containingFile.getManager().areElementsEquivalent(importedClass, element)) {
        String description = JavaErrorBundle.message("single.import.class.conflict", formatClass(importedClass));
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description);
      }
      importedClasses.put(name, Pair.pair(null, (PsiClass)element));
    }
    return null;
  }


  static HighlightInfo.Builder checkMustBeThrowable(@NotNull PsiType type, @NotNull PsiElement context, boolean addCastIntention) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    PsiClassType throwable = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, context.getResolveScope());
    if (!TypeConversionUtil.isAssignable(throwable, type)) {
      HighlightInfo.Builder highlightInfo = createIncompatibleTypeHighlightInfo(throwable, type, context.getTextRange(), 0);
      if (addCastIntention && TypeConversionUtil.areTypesConvertible(type, throwable)) {
        if (context instanceof PsiExpression) {
          IntentionAction action = getFixFactory().createAddTypeCastFix(throwable, (PsiExpression)context);
          highlightInfo.registerFix(action, null, null, null, null);
        }
      }

      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
      if (aClass != null) {
        IntentionAction action = getFixFactory().createExtendsListFix(aClass, throwable, true);
        highlightInfo.registerFix(action, null, null, null, null);
      }
      return highlightInfo;
    }
    return null;
  }


  private static HighlightInfo.Builder checkMustBeThrowable(@NotNull PsiClass aClass, @NotNull PsiElement context) {
    PsiClassType type = JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass);
    return checkMustBeThrowable(type, context, false);
  }

  static HighlightInfo.Builder checkReference(@NotNull PsiJavaCodeReferenceElement ref,
                                      @NotNull JavaResolveResult result,
                                      @NotNull PsiFile containingFile,
                                      @NotNull LanguageLevel languageLevel) {
    PsiElement refName = ref.getReferenceNameElement();
    if (!(refName instanceof PsiIdentifier) && !(refName instanceof PsiKeyword)) return null;
    PsiElement resolved = result.getElement();

    PsiElement refParent = ref.getParent();

    PsiElement granny;
    if (refParent instanceof PsiReferenceExpression && (granny = refParent.getParent()) instanceof PsiMethodCallExpression) {
      PsiReferenceExpression referenceToMethod = ((PsiMethodCallExpression)granny).getMethodExpression();
      PsiExpression qualifierExpression = referenceToMethod.getQualifierExpression();
      if (qualifierExpression == ref && resolved != null && !(resolved instanceof PsiClass) && !(resolved instanceof PsiVariable)) {
        String message = JavaErrorBundle.message("qualifier.must.be.expression");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(qualifierExpression).descriptionAndTooltip(message)
          ;
      }
    }
    else if (refParent instanceof PsiMethodCallExpression) {
      return null;  // methods checked elsewhere
    }

    if (resolved == null) {
      // do not highlight unknown packages (javac does not care), Javadoc, and module references (checked elsewhere)
      PsiJavaCodeReferenceElement parent = getOuterReferenceParent(ref);
      PsiElement outerParent = parent.getParent();
      if (outerParent instanceof PsiPackageStatement ||
          result.isPackagePrefixPackageReference() ||
          PsiUtil.isInsideJavadocComment(ref) ||
          parent.resolve() instanceof PsiMember ||
          outerParent instanceof PsiPackageAccessibilityStatement) {
        return null;
      }

      JavaResolveResult[] results = ref.multiResolve(true);
      String description;
      if (results.length > 1) {
        String t1 = format(Objects.requireNonNull(results[0].getElement()));
        String t2 = format(Objects.requireNonNull(results[1].getElement()));
        description = JavaErrorBundle.message("ambiguous.reference", refName.getText(), t1, t2);
      }
      else {
        description = JavaErrorBundle.message("cannot.resolve.symbol", refName.getText());
        if (ref instanceof PsiReferenceExpression expression) {
          PsiExpression qualifierExpression = expression.getQualifierExpression();
          if (qualifierExpression != null &&
              qualifierExpression.getType() instanceof PsiPrimitiveType primitiveType &&
              !primitiveType.equals(PsiTypes.nullType()) && !primitiveType.equals(PsiTypes.voidType())) {
            description = JavaErrorBundle.message("cannot.access.member.on.type", qualifierExpression.getText(),
                                                  primitiveType.getPresentableText(false));
          }
        }
      }

      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refName).descriptionAndTooltip(description);
      if (outerParent instanceof PsiNewExpression newExpression && isCallToStaticMember(newExpression)) {
        var action = new RemoveNewKeywordFix(newExpression);
        info.registerFix(action, null, null, null, null);
      }
      UnresolvedReferenceQuickFixUpdater.getInstance(containingFile.getProject()).registerQuickFixesLater(ref, info);

      return info;
    }

    boolean skipValidityChecks =
      PsiUtil.isInsideJavadocComment(ref) ||
      PsiTreeUtil.getParentOfType(ref, PsiPackageStatement.class, true) != null ||
      resolved instanceof PsiPackage && ref.getParent() instanceof PsiJavaCodeReferenceElement;
    if (!skipValidityChecks && !result.isValidResult()) {
      if (!result.isAccessible()) {
        Pair<@Nls String, List<IntentionAction>> problem = accessProblemDescriptionAndFixes(ref, resolved, result);
        boolean moduleAccessProblem = problem.second != null;
        PsiElement range = moduleAccessProblem ? findPackagePrefix(ref) : refName;
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(range).descriptionAndTooltip(problem.first);
        if (moduleAccessProblem) {
          problem.second.forEach(fix -> info.registerFix(fix, List.of(), null, null, null));
        }
        else if (result.isStaticsScopeCorrect() && resolved instanceof PsiJvmMember) {
          HighlightFixUtil.registerAccessQuickFixAction(info, range.getTextRange(), (PsiJvmMember)resolved, ref, result.getCurrentFileResolveScope(), null);
          if (ref instanceof PsiReferenceExpression) {
            IntentionAction action = getFixFactory().createRenameWrongRefFix((PsiReferenceExpression)ref);
            info.registerFix(action, null, null, null, null);
          }
        }
        UnresolvedReferenceQuickFixUpdater.getInstance(containingFile.getProject()).registerQuickFixesLater(ref, info);
        return info;
      }

      if (!result.isStaticsScopeCorrect()) {
        String description = staticContextProblemDescription(resolved);
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refName).descriptionAndTooltip(description);
        HighlightFixUtil.registerStaticProblemQuickFixAction(info, resolved, ref);
        if (ref instanceof PsiReferenceExpression) {
          IntentionAction action = getFixFactory().createRenameWrongRefFix((PsiReferenceExpression)ref);
          info.registerFix(action, null, null, null, null);
        }
        return info;
      }
    }

    if ((resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) && !(resolved instanceof ImplicitVariable)) {
      return HighlightControlFlowUtil.checkVariableMustBeFinal((PsiVariable)resolved, ref, languageLevel);
    }

    if (resolved instanceof PsiClass &&
        ((PsiClass)resolved).getContainingClass() == null &&
        PsiUtil.isFromDefaultPackage(resolved) &&
        (PsiTreeUtil.getParentOfType(ref, PsiImportStatementBase.class) != null ||
         PsiUtil.isModuleFile(containingFile) ||
         !PsiUtil.isFromDefaultPackage(containingFile))) {
      String description = JavaErrorBundle.message("class.in.default.package", ((PsiClass)resolved).getName());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refName).descriptionAndTooltip(description);
    }

    return null;
  }

  /**
   * Checks if the specified element is possibly a reference to a static member of a class,
   * when the {@code new} keyword is removed.
   * The element is split into two parts: the qualifier and the reference element.
   * If they both exist and the qualifier references a class and the reference element text matches either
   * the name of a static field or the name of a static method of the class
   * then the method returns true
   *
   * @param element an element to examine
   * @return true if the new expression can actually be a call to a class member (field or method), false otherwise.
   */
  @Contract(value = "null -> false", pure = true)
  static boolean isCallToStaticMember(@Nullable PsiElement element) {
    if (!(element instanceof PsiNewExpression newExpression)) return false;

    PsiJavaCodeReferenceElement reference = newExpression.getClassOrAnonymousClassReference();
    if (reference == null) return false;

    PsiElement qualifier = reference.getQualifier();
    PsiElement memberName = reference.getReferenceNameElement();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement referenceElement) || memberName == null) return false;

    PsiClass clazz = tryCast(referenceElement.resolve(), PsiClass.class);
    if (clazz == null) return false;

    if (newExpression.getArgumentList() == null) {
      PsiField field = clazz.findFieldByName(memberName.getText(), true);
      return field != null && field.hasModifierProperty(PsiModifier.STATIC);
    }
    PsiMethod[] methods = clazz.findMethodsByName(memberName.getText(), true);
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = method.getContainingClass();
        assert containingClass != null;
        if (!containingClass.isInterface() || containingClass == clazz) {
          // a static method in an interface is not resolvable from its subclasses
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  private static PsiElement findPackagePrefix(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement candidate = ref;
    while (candidate instanceof PsiJavaCodeReferenceElement) {
      if (((PsiJavaCodeReferenceElement)candidate).resolve() instanceof PsiPackage) return candidate;
      candidate = ((PsiJavaCodeReferenceElement)candidate).getQualifier();
    }
    return ref;
  }

  @NlsSafe
  @NotNull
  static String format(@NotNull PsiElement element) {
    if (element instanceof PsiClass) return formatClass((PsiClass)element);
    if (element instanceof PsiMethod) return JavaHighlightUtil.formatMethod((PsiMethod)element);
    if (element instanceof PsiField) return formatField((PsiField)element);
    if (element instanceof PsiLabeledStatement) return ((PsiLabeledStatement)element).getName() + ':';
    return ElementDescriptionUtil.getElementDescription(element, HighlightUsagesDescriptionLocation.INSTANCE);
  }

  @NotNull
  private static PsiJavaCodeReferenceElement getOuterReferenceParent(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiJavaCodeReferenceElement element = ref;
    while (true) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        element = (PsiJavaCodeReferenceElement)parent;
      }
      else {
        break;
      }
    }
    return element;
  }

  static HighlightInfo.Builder checkPackageAndClassConflict(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiFile containingFile) {
    if (ref.isQualified() && getOuterReferenceParent(ref).getParent() instanceof PsiPackageStatement) {
      Module module = ModuleUtilCore.findModuleForFile(containingFile);
      if (module != null) {
        GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(false);
        PsiClass aClass = JavaPsiFacade.getInstance(ref.getProject()).findClass(ref.getCanonicalText(), scope);
        if (aClass != null) {
          String message = JavaErrorBundle.message("package.clashes.with.class", ref.getText());
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(message);
        }
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkElementInReferenceList(@NotNull PsiJavaCodeReferenceElement ref,
                                                           @NotNull PsiReferenceList referenceList,
                                                           @NotNull JavaResolveResult resolveResult) {
    PsiElement resolved = resolveResult.getElement();
    HighlightInfo.Builder builder = null;
    PsiElement refGrandParent = referenceList.getParent();
    if (resolved instanceof PsiClass aClass) {
      if (refGrandParent instanceof PsiClass parentClass) {
        if (refGrandParent instanceof PsiTypeParameter typeParameter) {
          builder = GenericsHighlightUtil.checkElementInTypeParameterExtendsList(referenceList, typeParameter, resolveResult, ref);
        }
        else if (referenceList.equals(parentClass.getImplementsList()) ||
                 referenceList.equals(parentClass.getExtendsList())) {
          builder = HighlightClassUtil.checkExtendsClassAndImplementsInterface(referenceList, resolveResult, ref);
          if (builder == null) {
            builder = HighlightClassUtil.checkCannotInheritFromFinal(aClass, ref);
          }
          if (builder == null) {
            builder = HighlightClassUtil.checkExtendsProhibitedClass(aClass, parentClass, ref);
          }
          if (builder == null) {
            builder = GenericsHighlightUtil.checkCannotInheritFromTypeParameter(aClass, ref);
          }
          if (builder == null) {
            builder = HighlightClassUtil.checkExtendsSealedClass(parentClass, aClass, ref);
          }
        }
      }
      else if (refGrandParent instanceof PsiMethod method && method.getThrowsList() == referenceList) {
        builder = checkMustBeThrowable(aClass, ref);
      }
    }
    else if (refGrandParent instanceof PsiMethod method && referenceList == method.getThrowsList()) {
      String description = JavaErrorBundle.message("class.name.expected");
      builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(description);
    }
    return builder;
  }


  public static boolean isSerializationImplicitlyUsedField(@NotNull PsiField field) {
    String name = field.getName();
    if (!CommonClassNames.SERIAL_VERSION_UID_FIELD_NAME.equals(name) && !SERIAL_PERSISTENT_FIELDS_FIELD_NAME.equals(name)) return false;
    if (!field.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = field.getContainingClass();
    return aClass == null || JavaHighlightUtil.isSerializable(aClass);
  }

  static HighlightInfo.Builder checkClassReferenceAfterQualifier(@NotNull PsiReferenceExpression expression, @Nullable PsiElement resolved) {
    if (!(resolved instanceof PsiClass)) return null;
    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier == null) return null;
    if (qualifier instanceof PsiReferenceExpression) {
      PsiElement qualifierResolved = ((PsiReferenceExpression)qualifier).resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) return null;

      if (qualifierResolved == null) {
        PsiReferenceExpression qExpression = (PsiReferenceExpression)qualifier;
        while (true) {
          PsiElement qResolve = qExpression.resolve();
          if (qResolve == null || qResolve instanceof PsiClass || qResolve instanceof PsiPackage) {
            PsiExpression qualifierExpression = qExpression.getQualifierExpression();
            if (qualifierExpression == null) return null;
            if (qualifierExpression instanceof PsiReferenceExpression) {
              qExpression = (PsiReferenceExpression)qualifierExpression;
              continue;
            }
          }
          break;
        }
      }
    }
    String description = JavaErrorBundle.message("expected.class.or.package");
    HighlightInfo.Builder info =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description);
    IntentionAction action = getFixFactory().createRemoveQualifierFix(qualifier, expression, (PsiClass)resolved);
    info.registerFix(action, null, null, null, null);
    return info;
  }

  static HighlightInfo.Builder checkAnnotationMethodParameters(@NotNull PsiParameterList list) {
    PsiElement parent = list.getParent();
    if (PsiUtil.isAnnotationMethod(parent) &&
        (!list.isEmpty() || PsiTreeUtil.getChildOfType(list, PsiReceiverParameter.class) != null)) {
      String message = JavaErrorBundle.message("annotation.interface.members.may.not.have.parameters");
      HighlightInfo.Builder highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(message);
      IntentionAction action = getFixFactory().createRemoveParameterListFix((PsiMethod)parent);
      highlightInfo.registerFix(action, null, null, null, null);
      return highlightInfo;
    }
    return null;
  }

  static HighlightInfo.Builder checkForStatement(@NotNull PsiForStatement statement) {
    PsiStatement init = statement.getInitialization();
    if (init == null ||
        init instanceof PsiEmptyStatement ||
        init instanceof PsiDeclarationStatement &&
        ArrayUtil.getFirstElement(((PsiDeclarationStatement)init).getDeclaredElements()) instanceof PsiLocalVariable ||
        init instanceof PsiExpressionStatement ||
        init instanceof PsiExpressionListStatement) {
      return null;
    }

    String message = JavaErrorBundle.message("invalid.statement");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(init).descriptionAndTooltip(message);
  }

  @NotNull
  private static LanguageLevel getApplicableLevel(@NotNull PsiFile file, @NotNull HighlightingFeature feature) {
    LanguageLevel standardLevel = feature.getStandardLevel();
    if (feature.level.isPreview()) {
      JavaSdkVersion sdkVersion = JavaSdkVersionUtil.getJavaSdkVersion(file);
      if (sdkVersion != null) {
        if (standardLevel != null && sdkVersion.isAtLeast(JavaSdkVersion.fromLanguageLevel(standardLevel))) {
          return standardLevel;
        }
        LanguageLevel previewLevel = sdkVersion.getMaxLanguageLevel().getPreviewLevel();
        if (previewLevel != null && previewLevel.isAtLeast(feature.level)) {
          return previewLevel;
        }
      }
    }
    return feature.level;
  }

  static HighlightInfo.Builder checkFeature(@NotNull PsiElement element,
                                           @NotNull HighlightingFeature feature,
                                           @NotNull LanguageLevel level,
                                           @NotNull PsiFile file) {
    return checkFeature(element, feature, level, file, null, HighlightInfoType.ERROR);
  }

  static HighlightInfo.Builder checkFeature(@NotNull PsiElement element,
                                    @NotNull HighlightingFeature feature,
                                    @NotNull LanguageLevel level,
                                    @NotNull PsiFile file,
                                    @Nullable @NlsContexts.DetailedDescription String message,
                                    @NotNull HighlightInfoType highlightInfoType) {
    if (file.getManager().isInProject(file) && !feature.isSufficient(level)) {
      message = message == null ? getUnsupportedFeatureMessage(feature, level, file) : message;
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(highlightInfoType).range(element).descriptionAndTooltip(message);
      List<IntentionAction> registrar = new ArrayList<>();
      registerIncreaseLanguageLevelFixes(file, feature, registrar);
      for (IntentionAction action : registrar) {
        info.registerFix(action, null, null, null, null);
      }
      return info;
    }

    return null;
  }

  static HighlightInfo.Builder checkFeature(@NotNull TextRange range,
                                    @NotNull HighlightingFeature feature,
                                    @NotNull LanguageLevel level,
                                    @NotNull PsiFile file) {
    if (file.getManager().isInProject(file) && !feature.isSufficient(level)) {
      String message = getUnsupportedFeatureMessage(feature, level, file);
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message);
      List<IntentionAction> registrar = new ArrayList<>();
      registerIncreaseLanguageLevelFixes(file, feature, registrar);
      for (IntentionAction action : registrar) {
        info.registerFix(action, null, null, null, null);
      }
      return info;
    }

    return null;
  }

  public static void registerIncreaseLanguageLevelFixes(@NotNull PsiElement element,
                                                        @NotNull HighlightingFeature feature,
                                                        @NotNull List<? super IntentionAction> registrar) {
    if (feature.isAvailable(element)) return;
    LanguageLevel applicableLevel = getApplicableLevel(element.getContainingFile(), feature);
    registrar.add(getFixFactory().createIncreaseLanguageLevelFix(applicableLevel));
    registrar.add(getFixFactory().createUpgradeSdkFor(applicableLevel));
    registrar.add(getFixFactory().createShowModulePropertiesFix(element));
  }

  private static @NotNull @NlsContexts.DetailedDescription String getUnsupportedFeatureMessage(@NotNull HighlightingFeature feature,
                                                                                               @NotNull LanguageLevel level,
                                                                                               @NotNull PsiFile file) {
    String name = JavaAnalysisBundle.message(feature.key);
    String version = JavaSdkVersion.fromLanguageLevel(level).getDescription();
    String message = JavaErrorBundle.message("insufficient.language.level", name, version);

    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module != null) {
      LanguageLevel moduleLanguageLevel = LanguageLevelUtil.getEffectiveLanguageLevel(module);
      if (moduleLanguageLevel.isAtLeast(feature.level)) {
        for (FilePropertyPusher<?> pusher : FilePropertyPusher.EP_NAME.getExtensions()) {
          if (pusher instanceof JavaLanguageLevelPusher) {
            String newMessage = ((JavaLanguageLevelPusher)pusher).getInconsistencyLanguageLevelMessage(message, level, file);
            if (newMessage != null) {
              return newMessage;
            }
          }
        }
      }
    }

    return message;
  }
}
