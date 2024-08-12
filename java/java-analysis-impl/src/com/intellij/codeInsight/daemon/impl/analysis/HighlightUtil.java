// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.*;
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
import com.intellij.lang.injection.InjectedLanguageManager;
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
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.index.JavaImplicitClassIndex;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.ElementType;
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
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.tryCast;

// generates HighlightInfoType.ERROR-like HighlightInfos
public final class HighlightUtil {
  public static final Set<String> RESTRICTED_RECORD_COMPONENT_NAMES = Set.of(
    "clone", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait");

  private static final Logger LOG = Logger.getInstance(HighlightUtil.class);

  private static final Map<String, Set<String>> ourInterfaceIncompatibleModifiers = Map.of(
    PsiModifier.ABSTRACT, Set.of(),
    PsiModifier.PACKAGE_LOCAL, Set.of(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED),
    PsiModifier.PRIVATE, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED),
    PsiModifier.PUBLIC, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED),
    PsiModifier.PROTECTED, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE),
    PsiModifier.STRICTFP, Set.of(),
    PsiModifier.STATIC, Set.of(),
    PsiModifier.SEALED, Set.of(PsiModifier.NON_SEALED),
    PsiModifier.NON_SEALED, Set.of(PsiModifier.SEALED));
  private static final Map<String, Set<String>> ourMethodIncompatibleModifiers = Map.ofEntries(
    Map.entry(PsiModifier.ABSTRACT, Set.of(
      PsiModifier.NATIVE, PsiModifier.STATIC, PsiModifier.FINAL, PsiModifier.PRIVATE, PsiModifier.STRICTFP, PsiModifier.SYNCHRONIZED,
      PsiModifier.DEFAULT)),
    Map.entry(PsiModifier.NATIVE, Set.of(PsiModifier.ABSTRACT, PsiModifier.STRICTFP)),
    Map.entry(PsiModifier.PACKAGE_LOCAL, Set.of(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED)),
    Map.entry(PsiModifier.PRIVATE, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED)),
    Map.entry(PsiModifier.PUBLIC, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED)),
    Map.entry(PsiModifier.PROTECTED, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE)),
    Map.entry(PsiModifier.STATIC, Set.of(PsiModifier.ABSTRACT, PsiModifier.DEFAULT)),
    Map.entry(PsiModifier.DEFAULT, Set.of(PsiModifier.ABSTRACT, PsiModifier.STATIC, PsiModifier.PRIVATE)),
    Map.entry(PsiModifier.SYNCHRONIZED, Set.of(PsiModifier.ABSTRACT)),
    Map.entry(PsiModifier.STRICTFP, Set.of(PsiModifier.ABSTRACT)),
    Map.entry(PsiModifier.FINAL, Set.of(PsiModifier.ABSTRACT)));
  private static final Map<String, Set<String>> ourFieldIncompatibleModifiers = Map.of(
    PsiModifier.FINAL, Set.of(PsiModifier.VOLATILE),
    PsiModifier.PACKAGE_LOCAL, Set.of(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED),
    PsiModifier.PRIVATE, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED),
    PsiModifier.PUBLIC, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED),
    PsiModifier.PROTECTED, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE),
    PsiModifier.STATIC, Set.of(),
    PsiModifier.TRANSIENT, Set.of(),
    PsiModifier.VOLATILE, Set.of(PsiModifier.FINAL));
  private static final Map<String, Set<String>> ourClassIncompatibleModifiers = Map.of(
    PsiModifier.ABSTRACT, Set.of(PsiModifier.FINAL),
    PsiModifier.FINAL, Set.of(PsiModifier.ABSTRACT, PsiModifier.SEALED, PsiModifier.NON_SEALED),
    PsiModifier.PACKAGE_LOCAL, Set.of(PsiModifier.PRIVATE, PsiModifier.PUBLIC, PsiModifier.PROTECTED),
    PsiModifier.PRIVATE, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PROTECTED),
    PsiModifier.PUBLIC, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE, PsiModifier.PROTECTED),
    PsiModifier.PROTECTED, Set.of(PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC, PsiModifier.PRIVATE),
    PsiModifier.STRICTFP, Set.of(),
    PsiModifier.STATIC, Set.of(),
    PsiModifier.SEALED, Set.of(PsiModifier.FINAL, PsiModifier.NON_SEALED),
    PsiModifier.NON_SEALED, Set.of(PsiModifier.FINAL, PsiModifier.SEALED));
  private static final Map<String, Set<String>> ourClassInitializerIncompatibleModifiers = Map.of(PsiModifier.STATIC, Set.of());
  private static final Map<String, Set<String>> ourModuleIncompatibleModifiers = Map.of(PsiModifier.OPEN, Set.of());
  private static final Map<String, Set<String>> ourRequiresIncompatibleModifiers = Map.of(
    PsiModifier.STATIC, Set.of(),
    PsiModifier.TRANSITIVE, Set.of());

  private static final Set<String> ourConstructorNotAllowedModifiers =
    Set.of(PsiModifier.ABSTRACT, PsiModifier.STATIC, PsiModifier.NATIVE, PsiModifier.FINAL, PsiModifier.STRICTFP, PsiModifier.SYNCHRONIZED);

  private static final String SERIAL_PERSISTENT_FIELDS_FIELD_NAME = "serialPersistentFields";
  public static final TokenSet BRACKET_TOKENS = TokenSet.create(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET);
  private static final @NlsSafe String ANONYMOUS = "anonymous ";

  private HighlightUtil() { }

  @NotNull
  private static QuickFixFactory getFixFactory() {
    return QuickFixFactory.getInstance();
  }

  private static String getIncompatibleModifier(@NotNull String modifier,
                                                @NotNull PsiModifierList modifierList,
                                                @NotNull Map<String, Set<String>> incompatibleModifiersHash) {
    // modifier is always incompatible with itself
    int modifierCount = 0;
    for (PsiElement otherModifier = modifierList.getFirstChild(); otherModifier != null; otherModifier = otherModifier.getNextSibling()) {
      if (modifier.equals(otherModifier.getText())) modifierCount++;
    }
    if (modifierCount > 1) return modifier;

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


  static void checkInstanceOfApplicable(@NotNull PsiInstanceOfExpression expression, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiExpression operand = expression.getOperand();
    PsiTypeElement typeElement = InstanceOfUtils.findCheckTypeElement(expression);
    if (typeElement == null) return;
    PsiType checkType = typeElement.getType();
    PsiType operandType = operand.getType();
    if (operandType == null) return;
    boolean operandIsPrimitive = TypeConversionUtil.isPrimitiveAndNotNull(operandType);
    boolean checkIsPrimitive = TypeConversionUtil.isPrimitiveAndNotNull(checkType);
    boolean convertible = TypeConversionUtil.areTypesConvertible(operandType, checkType);
    boolean primitiveInPatternsEnabled = PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, expression);
    if (((operandIsPrimitive || checkIsPrimitive) && !primitiveInPatternsEnabled) || !convertible) {
      if (!convertible && IncompleteModelUtil.isIncompleteModel(expression) &&
          IncompleteModelUtil.isPotentiallyConvertible(checkType, operand)) {
        return;
      }
      String message = JavaErrorBundle.message("inconvertible.type.cast", JavaHighlightUtil.formatType(operandType), JavaHighlightUtil
        .formatType(checkType));
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(message);
      if (checkIsPrimitive) {
        IntentionAction action = getFixFactory().createReplacePrimitiveWithBoxedTypeAction(operandType, typeElement);
        if (action != null) {
          info.registerFix(action, null, null, null, null);
        }
      }

      if (((operandIsPrimitive || checkIsPrimitive) && !primitiveInPatternsEnabled) && convertible) {
        registerIncreaseLanguageLevelFixes(expression, JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, info);
      }

      errorSink.accept(info);
      return;
    }
    PsiPrimaryPattern pattern = expression.getPattern();
    if (pattern instanceof PsiDeconstructionPattern deconstruction) {
      PatternHighlightingModel.createDeconstructionErrors(deconstruction, errorSink);
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
    HighlightInfo.Builder info = checkFeature(expression, JavaFeature.INTERSECTION_CASTS, languageLevel, file);
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
            var action = new FlipIntersectionSidesFix(aClass.getName(), conjunct, castTypeElement);
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
          var action = new DeleteRepeatedInterfaceFix(conjunct);
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
      if (IncompleteModelUtil.isIncompleteModel(expression) && IncompleteModelUtil.isPotentiallyConvertible(castType, operand)) {
        return null;
      }
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
    if (lValue != null && !TypeConversionUtil.isLValue(lValue) && !PsiTreeUtil.hasErrorElements(expression) &&
        !(IncompleteModelUtil.isIncompleteModel(expression) &&
          PsiUtil.skipParenthesizedExprDown(lValue) instanceof PsiReferenceExpression ref &&
          IncompleteModelUtil.canBePendingReference(ref))) {
      String description = JavaErrorBundle.message("variable.expected");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(lValue).descriptionAndTooltip(description);
    }
    return null;
  }

  /**
   * JEP 440-441
   * Any variable that is used but not declared by a guard must either be final or effectively final (4.12.4)
   * and cannot be assigned to (15.26),
   * incremented (15.14.2), or decremented (15.14.3), otherwise a compile-time error occurs
   **/
  @Nullable
  static HighlightInfo.Builder checkOutsideDeclaredCantBeAssignmentInGuard(@Nullable PsiExpression expressionVariable) {
    if (expressionVariable == null) {
      return null;
    }
    if (!PsiUtil.isAccessedForWriting(expressionVariable)) {
      return null;
    }
    PsiSwitchLabelStatementBase label = PsiTreeUtil.getParentOfType(expressionVariable, PsiSwitchLabelStatementBase.class);
    if (label == null) {
      return null;
    }
    PsiExpression guardingExpression = label.getGuardExpression();
    if (!PsiTreeUtil.isAncestor(guardingExpression, expressionVariable, false)) {
      return null;
    }
    if (!(expressionVariable instanceof PsiReferenceExpression referenceExpression &&
          referenceExpression.resolve() instanceof PsiVariable psiVariable)) {
      return null;
    }
    if (PsiTreeUtil.isAncestor(guardingExpression, psiVariable, false)) {
      return null;
    }
    String message = JavaErrorBundle.message("impossible.assign.declared.outside.guard", psiVariable.getName());
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(expressionVariable)
      .descriptionAndTooltip(message);
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
      if (IncompleteModelUtil.isIncompleteModel(assignment) && IncompleteModelUtil.isPotentiallyConvertible(lType, rExpr)) {
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
    if (rType == null || toType == null) return false;
    boolean convertible = expression instanceof PsiNewExpression ? toType.isAssignableFrom(rType) : toType.isConvertibleFrom(rType);
    return convertible && toType.isAssignableFrom(castType);
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
      if (PsiTypes.nullType().equals(lType) && 
          ExpressionUtils.nonStructuralChildren(initializer).allMatch(ExpressionUtils::isNullLiteral)) {
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
    else if (TypeConversionUtil.areTypesAssignmentCompatible(lType, expression) || PsiTreeUtil.hasErrorElements(expression)) {
      return null;
    }
    if (rType == null) {
      rType = expression.getType();
    }
    if (lType == null || lType == PsiTypes.nullType()) {
      return null;
    }
    if (expression != null && IncompleteModelUtil.isIncompleteModel(expression) &&
        IncompleteModelUtil.isPotentiallyConvertible(lType, expression)) {
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
    HighlightInfo.Builder errorResult;
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
          boolean constructor = method != null && method.isConstructor();
          if (constructor) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null && !method.getName().equals(containingClass.getName())) {
              return null;
            }
          }
          description = JavaErrorBundle.message(constructor ? "return.from.constructor" : "return.from.void.method");
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
      else if (!isMethodVoid && !PsiTreeUtil.hasErrorElements(statement)) {
        description = JavaErrorBundle.message("missing.return.value");
        errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description)
          .navigationShift(PsiKeyword.RETURN.length());
        IntentionAction action = getFixFactory().createMethodReturnFix(method, PsiTypes.voidType(), true);
        errorResult.registerFix(action, null, null, null, null);
      }
      else {
        errorResult = null;
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
    if (variable instanceof ExternallyDefinedPsiElement || variable.isUnnamed()) return null;
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
      PsiElement parent = identifier.getParent();
      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_9) && !(parent instanceof PsiUnnamedPattern) &&
          !(parent instanceof PsiVariable var && var.isUnnamed())) {
        String text = JavaFeature.UNNAMED_PATTERNS_AND_VARIABLES.isSufficient(languageLevel) ?
                      JavaErrorBundle.message("underscore.identifier.error.unnamed") :
                      JavaErrorBundle.message("underscore.identifier.error");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(text);
      }
      else if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        if (parent instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiLambdaExpression &&
            !parameter.isUnnamed()) {
          String text = JavaErrorBundle.message("underscore.lambda.identifier");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(text);
        }
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkUnnamedVariableDeclaration(@NotNull PsiVariable variable) {
    if (isArrayDeclaration(variable)) {
      IntentionAction fix = new NormalizeBracketsFix(variable).asIntention();
      TokenSet brackets = TokenSet.create(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET);
      TextRange range = StreamEx.of(variable.getChildren())
        .filter(t -> PsiUtil.isJavaToken(t, brackets))
        .map(PsiElement::getTextRangeInParent)
        .reduce(TextRange::union)
        .orElseThrow()
        .shiftRight(variable.getTextRange().getStartOffset());// Must have at least one
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(
        JavaAnalysisBundle.message("error.unnamed.variable.brackets")).registerFix(fix, null, null, null, null);
    }
    if (variable instanceof PsiPatternVariable) return null;
    if (variable instanceof PsiResourceVariable) return null;
    String message;
    IntentionAction fix = null;
    if (variable instanceof PsiLocalVariable local) {
      if (local.getInitializer() != null) return null;
      message = JavaAnalysisBundle.message("error.unnamed.variable.without.initializer");
      fix = getFixFactory().createAddVariableInitializerFix(local);
    }
    else if (variable instanceof PsiParameter parameter) {
      PsiElement scope = parameter.getDeclarationScope();
      if (!(scope instanceof PsiMethod)) return null;
      message = JavaAnalysisBundle.message("error.unnamed.method.parameter.not.allowed");
    }
    else if (variable instanceof PsiField) {
      message = JavaAnalysisBundle.message("error.unnamed.field.not.allowed");
    }
    else {
      message = JavaAnalysisBundle.message("error.unnamed.variable.not.allowed.in.this.context");
    }
    TextRange range = TextRange.create(variable.getTextRange().getStartOffset(),
                                       Objects.requireNonNull(variable.getNameIdentifier()).getTextRange().getEndOffset());
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message);
    if (fix != null) {
      builder.registerFix(fix, null, null, null, null);
    }
    return builder;
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
    unhandled = ContainerUtil.filter(unhandled, type -> type.resolve() != null);
    if (unhandled.isEmpty()) return null;

    HighlightInfoType highlightType = getUnhandledExceptionHighlightType(element);
    if (highlightType == null) return null;

    TextRange textRange = computeRange(element);
    String description = getUnhandledExceptionsDescriptor(unhandled);
    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(highlightType).range(textRange).descriptionAndTooltip(description);
    HighlightFixUtil.registerUnhandledExceptionFixes(element, info);
    ErrorFixExtensionPoint.registerFixes(info, element, "unhandled.exceptions");
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

    if (JavaFeature.ENHANCED_SWITCH.isSufficient(level)) {
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
      String message;
      if (incompatible.equals(modifier)) {
        for (PsiElement child = modifierList.getFirstChild(); child != null; child = child.getNextSibling()) {
          if (modifier.equals(child.getText())) {
            if (child == keyword) return null;
            else break;
          }
        }
        message = JavaErrorBundle.message("repeated.modifier", incompatible);
      }
      else {
        message = JavaErrorBundle.message("incompatible.modifiers", modifier, incompatible);
      }
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
    String message = null;
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
                       PsiUtil.isAvailable(JavaFeature.INNER_STATICS, modifierOwnerParent) ||
                       ((PsiClass)modifierOwnerParent).getQualifiedName() != null ||
                       !modifierOwnerParent.isPhysical());
        }
        else {
          if (PsiModifier.STATIC.equals(modifier) || privateOrProtected || PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
            isAllowed = modifierOwnerParent instanceof PsiClass &&
                        (PsiModifier.STATIC.equals(modifier) ||
                         PsiUtil.isAvailable(JavaFeature.INNER_STATICS, modifierOwnerParent) ||
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
            privateOrProtected && !PsiUtil.getLanguageLevel(modifierOwnerParent).isAtLeast(LanguageLevel.JDK_16)) {
          isAllowed = false;
        }
      }
      if ((PsiModifier.NON_SEALED.equals(modifier) || PsiModifier.SEALED.equals(modifier)) &&
          modifierOwnerParent instanceof PsiDeclarationStatement) {
        isAllowed = false; // JLS 14.3
        message = JavaErrorBundle.message("modifier.not.allowed.on.local.classes", modifier);
      }
      else if (PsiModifier.NON_SEALED.equals(modifier) && !aClass.hasModifierProperty(PsiModifier.SEALED)) {
        isAllowed = Arrays.stream(aClass.getSuperTypes())
          .map(PsiClassType::resolve)
          .anyMatch(superClass -> superClass != null && superClass.hasModifierProperty(PsiModifier.SEALED));
        message = JavaErrorBundle.message("modifier.not.allowed.on.classes.without.sealed.super");
      }
    }
    else if (modifierOwner instanceof PsiMethod method) {
      isAllowed = !(method.isConstructor() && ourConstructorNotAllowedModifiers.contains(modifier));
      PsiClass containingClass = method.getContainingClass();
      if ((method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED)) && method.isConstructor() &&
          containingClass != null && containingClass.isEnum()) {
        isAllowed = false;
      }

      boolean isInterface = modifierOwnerParent instanceof PsiClass psiClass && psiClass.isInterface();
      if (PsiModifier.PRIVATE.equals(modifier) && modifierOwnerParent instanceof PsiClass) {
        isAllowed &= !isInterface || PsiUtil.isAvailable(JavaFeature.PRIVATE_INTERFACE_METHODS, modifierOwner) && !((PsiClass)modifierOwnerParent).isAnnotationType();
      }
      else if (PsiModifier.STRICTFP.equals(modifier)) {
        isAllowed &= !isInterface || PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, modifierOwner);
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
      if (message == null) message = JavaErrorBundle.message("modifier.not.allowed", modifier);
      HighlightInfo.Builder highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(message);
      IntentionAction action = fix != null ? fix : getFixFactory().createModifierListFix(modifierList, modifier, false, false);
      highlightInfo.registerFix(action, null, null, null, null);
      return highlightInfo;
    }

    return null;
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
          HighlightInfo.Builder info = checkFeature(expression, JavaFeature.HEX_FP_LITERALS, level, file);
          if (info != null) {
            if (description != null) {
              description.set(getUnsupportedFeatureMessage(JavaFeature.HEX_FP_LITERALS, level, file));
            }
            return info;
          }
        }
      }
      if (isInt) {
        if (text.startsWith(PsiLiteralUtil.BIN_PREFIX)) {
          HighlightInfo.Builder info = checkFeature(expression, JavaFeature.BIN_LITERALS, level, file);
          if (info != null) {
            if (description != null) {
              description.set(getUnsupportedFeatureMessage(JavaFeature.BIN_LITERALS, level, file));
            }
            return info;
          }
        }
      }
      if (isInt || isFP) {
        if (text.contains("_")) {
          HighlightInfo.Builder info = checkFeature(expression, JavaFeature.UNDERSCORES, level, file);
          if (info != null) {
            if (description != null) {
              description.set(getUnsupportedFeatureMessage(JavaFeature.UNDERSCORES, level, file));
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
          .descriptionAndTooltip(message);
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
      final HighlightInfo.Builder info = checkTextBlockEscapes(expression, rawText, level, file, description);
      if (info != null) return info;
    }
    else if (type == JavaTokenType.STRING_LITERAL || type == JavaTokenType.TEXT_BLOCK_LITERAL) {
      if (type == JavaTokenType.STRING_LITERAL) {
        for (PsiElement element = expression.getFirstChild(); element != null; element = element.getNextSibling()) {
          if (element instanceof OuterLanguageElement) {
            return null;
          }
        }

        if (!StringUtil.startsWithChar(text, '"')) return null;
        if (!StringUtil.endsWithChar(text, '"') || text.length() == 1) {
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
        final HighlightInfo.Builder info2 = checkTextBlockEscapes(expression, rawText, level, file, description);
        if (info2 != null) return info2;
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
          final HighlightInfo.Builder info = checkTextBlockNewlineAfterOpeningQuotes(expression, text, description);
          if (info != null) return info;
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

    if (value instanceof Float number) {
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
    else if (value instanceof Double number) {
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

  private static HighlightInfo.Builder checkTextBlockNewlineAfterOpeningQuotes(@NotNull PsiElement expression,
                                                                               String text,
                                                                               @Nullable Ref<? super String> description) {
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
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression, TextRange.from(0, 3)).descriptionAndTooltip(message);
    }
    return null;
  }

  public static HighlightInfo.Builder checkFragmentError(PsiFragment fragment) {
    String text = InjectedLanguageManager.getInstance(fragment.getProject()).getUnescapedText(fragment);
    if (fragment.getTokenType() == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) {
      final HighlightInfo.Builder info1 = checkTextBlockNewlineAfterOpeningQuotes(fragment, text, null);
      if (info1 != null) return info1;
    }
    int length = text.length();
    if (fragment.getTokenType() == JavaTokenType.STRING_TEMPLATE_END) {
      if (!StringUtil.endsWithChar(text, '\"') || length == 1) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(fragment)
          .descriptionAndTooltip(JavaErrorBundle.message("illegal.line.end.in.string.literal"));
      }
    }
    if (text.endsWith("\\{")) {
      text = text.substring(0, length - 2);
      length -= 2;
    }
    StringBuilder chars = new StringBuilder(length);
    int[] offsets = new int[length + 1];
    boolean success = CodeInsightUtilCore.parseStringCharacters(text, chars, offsets, fragment.isTextBlock());
    if (!success) {
      String message = JavaErrorBundle.message("illegal.escape.character.in.string.literal");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(fragment, calculateErrorRange(text, offsets[chars.length()]))
        .descriptionAndTooltip(message);
    }
    return null;
  }

  private static HighlightInfo.@Nullable Builder checkTextBlockEscapes(@NotNull PsiLiteralExpression expression,
                                                                       @NotNull String text,
                                                                       @NotNull LanguageLevel level,
                                                                       @Nullable PsiFile file,
                                                                       @Nullable Ref<? super String> description) {
    if (file == null) return null;
    TextRange errorRange = calculateUnescapedRange(text, "\\s", expression.getTextOffset());
    if (errorRange == null) return null;
    HighlightInfo.Builder info = checkFeature(errorRange, JavaFeature.TEXT_BLOCK_ESCAPES, level, file);
    if (info == null) return null;
    if (description != null) {
      description.set(getUnsupportedFeatureMessage(JavaFeature.TEXT_BLOCK_ESCAPES, level, file));
    }
    return info;
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

  private static TextRange calculateUnescapedRange(@NotNull String text, @NotNull String subText, int offset) {
    int start = 0;
    while ((start = StringUtil.indexOf(text, subText, start)) != -1) {
      int nSlashes = 0;
      for (int pos = start - 1; pos >= 0; pos--) {
        if (text.charAt(pos) != '\\') break;
        nSlashes++;
      }
      if (nSlashes % 2 == 0) {
        return TextRange.from(offset + start, subText.length());
      }
      start += subText.length();
    }
    return null;
  }

  private static final Pattern FP_LITERAL_PARTS =
    Pattern.compile("(?:" +
                    "0x([_\\p{XDigit}]*)\\.?([_\\p{XDigit}]*)p[+-]?([_\\d]*)" +
                    "|" +
                    "([_\\d]*)\\.?([_\\d]*)e?[+-]?([_\\d]*)" +
                    ")[fd]?");

  private static HighlightInfo.Builder checkUnderscores(@NotNull PsiElement expression, @NotNull String text, boolean isInt) {
    String[] parts;
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
      else {
        parts = ArrayUtilRt.EMPTY_STRING_ARRAY;
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

      if (!TypeConversionUtil.isBooleanType(type) && !PsiTreeUtil.hasErrorElements(expr)) {
        return createMustBeBooleanInfo(expr, type);
      }
    }
    return null;
  }

  @Nullable
  private static HighlightInfo.Builder createMustBeBooleanInfo(@NotNull PsiExpression expr, @Nullable PsiType type) {
    if (type == null && IncompleteModelUtil.isIncompleteModel(expr) && IncompleteModelUtil.mayHaveUnknownTypeDueToPendingReference(expr)) {
      return null;
    }
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


  static @NotNull UnhandledExceptions collectUnhandledExceptions(@NotNull PsiTryStatement statement) {
    UnhandledExceptions thrownTypes = UnhandledExceptions.EMPTY;

    PsiCodeBlock tryBlock = statement.getTryBlock();
    if (tryBlock != null) {
      thrownTypes = thrownTypes.merge(UnhandledExceptions.collect(tryBlock));
    }

    PsiResourceList resources = statement.getResourceList();
    if (resources != null) {
      thrownTypes = thrownTypes.merge(UnhandledExceptions.collect(resources));
    }

    return thrownTypes;
  }

  static void checkExceptionThrownInTry(@NotNull PsiParameter parameter,
                                        @NotNull Set<? extends PsiClassType> thrownTypes,
                                        @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiCatchSection)) return;

    PsiType caughtType = parameter.getType();
    if (caughtType instanceof PsiClassType) {
      HighlightInfo.Builder info = checkSimpleCatchParameter(parameter, thrownTypes, (PsiClassType)caughtType);
      if (info != null) {
        errorSink.accept(info);
      }
      return;
    }
    if (caughtType instanceof PsiDisjunctionType) {
      checkMultiCatchParameter(parameter, thrownTypes, errorSink);
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
                                               @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
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
        errorSink.accept(builder);
      }
    }
  }


  static void checkWithImprovedCatchAnalysis(@NotNull PsiParameter parameter,
                                             @NotNull Collection<? extends PsiClassType> thrownInTryStatement,
                                             @NotNull PsiFile containingFile, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
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
        errorSink.accept(builder);
      }
    }
  }


  static HighlightInfo.Builder checkNotAStatement(@NotNull PsiStatement statement) {
    if (PsiUtil.isStatement(statement)) {
      return null;
    }
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

  static void checkSwitchExpressionReturnTypeCompatible(@NotNull PsiSwitchExpression switchExpression,
                                                        @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
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
          errorSink.accept(info);
        }
      }

      if (PsiTypes.voidType().equals(switchExpressionType)) {
        String text = JavaErrorBundle.message("switch.expression.cannot.be.void");
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(switchExpression.getFirstChild()).descriptionAndTooltip(text);
        errorSink.accept(info);
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
    @Nullable PsiPattern expressionPattern = expression.getPattern();
    PsiTypeTestPattern pattern = tryCast(expressionPattern, PsiTypeTestPattern.class);
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

  static HighlightInfo.Builder checkPolyadicOperatorApplicable(@NotNull PsiPolyadicExpression expression) {
    PsiExpression[] operands = expression.getOperands();

    PsiType lType = operands[0].getType();
    IElementType operationSign = expression.getOperationTokenType();
    for (int i = 1; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      PsiType rType = operand.getType();
      if (!TypeConversionUtil.isBinaryOperatorApplicable(operationSign, lType, rType, false) &&
          !(IncompleteModelUtil.isIncompleteModel(expression) &&
            IncompleteModelUtil.isPotentiallyConvertible(lType, rType, expression))) {
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

    if (qualifier != null && aClass.isInterface() && expr instanceof PsiSuperExpression && 
        JavaFeature.EXTENSION_METHODS.isSufficient(languageLevel)) {
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
    if (JavaFeature.EXTENSION_METHODS.isSufficient(languageLevel) && qualifier instanceof PsiSuperExpression) {
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
    if (!(expr instanceof PsiSuperExpression) || qualifier == null || !JavaFeature.EXTENSION_METHODS.isSufficient(languageLevel)) return false;
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

  @Nullable
  @Nls
  static ErrorWithFixes checkModuleAccess(@NotNull PsiElement resolved, @NotNull PsiElement ref, @NotNull JavaResolveResult result) {
    PsiElement refElement = resolved;
    PsiClass packageLocalClass = HighlightFixUtil.getPackageLocalClassInTheMiddle(ref);
    if (packageLocalClass != null) {
      refElement = packageLocalClass;
    }

    String symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());
    String containerName = (resolved instanceof PsiModifierListOwner modifierListOwner)
                           ? getContainerName(modifierListOwner, result.getSubstitutor())
                           : null;
    return checkModuleAccess(resolved, ref, symbolName, containerName);
  }

  @Nullable
  @Nls
  private static ErrorWithFixes checkModuleAccess(@NotNull PsiElement target,
                                                  @NotNull PsiElement place,
                                                  @Nullable String symbolName,
                                                  @Nullable String containerName) {
    for (JavaModuleSystem moduleSystem : JavaModuleSystem.EP_NAME.getExtensionList()) {
      if (moduleSystem instanceof JavaModuleSystemEx system) {
        if (target instanceof PsiClass targetClass) {
          final ErrorWithFixes problem = system.checkAccess(targetClass, place);
          if (problem != null) return problem;
        }
        if (target instanceof PsiPackage targetPackage) {
          final ErrorWithFixes problem = system.checkAccess(targetPackage.getQualifiedName(), null, place);
          if (problem != null) return problem;
        }
      }
      else if (!isAccessible(moduleSystem, target, place)) {
        return new ErrorWithFixes(JavaErrorBundle.message("visibility.module.access.problem", symbolName, containerName, moduleSystem.getName()));
      }
    }
    return null;
  }

  private static boolean isAccessible(@NotNull JavaModuleSystem system, @NotNull PsiElement target, @NotNull PsiElement place) {
    if (target instanceof PsiClass) return system.isAccessible((PsiClass)target, place);
    if (target instanceof PsiPackage) return system.isAccessible(((PsiPackage)target).getQualifiedName(), null, place);
    return true;
  }

  private static PsiElement getContainer(@NotNull PsiModifierListOwner refElement) {
    for (ContainerProvider provider : ContainerProvider.EP_NAME.getExtensionList()) {
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

  static HighlightInfo.Builder checkTemplateExpression(@NotNull PsiTemplateExpression templateExpression) {
    HighlightInfo.Builder builder = checkFeature(templateExpression, JavaFeature.STRING_TEMPLATES,
                                                 PsiUtil.getLanguageLevel(templateExpression), templateExpression.getContainingFile());
    if (builder != null) return builder;
    PsiExpression processor = templateExpression.getProcessor();
    if (processor == null) {
      String message = JavaErrorBundle.message("processor.missing.from.string.template.expression");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(templateExpression).descriptionAndTooltip(message)
        .registerFix(new MissingStrProcessorFix(templateExpression), null, null, null, null);
    }
    PsiType type = processor.getType();
    if (type == null) return null;

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(processor.getProject());
    PsiClassType processorType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING_TEMPLATE_PROCESSOR, processor.getResolveScope());
    if (!TypeConversionUtil.isAssignable(processorType, type)) {
      if (IncompleteModelUtil.isIncompleteModel(templateExpression) && IncompleteModelUtil.isPotentiallyConvertible(processorType, processor)) return null;
      return createIncompatibleTypeHighlightInfo(processorType, type, processor.getTextRange(), 0);
    }

    PsiClass processorClass = processorType.resolve();
    if (processorClass == null) return null;
    for (PsiClassType classType : PsiTypesUtil.getClassTypeComponents(type)) {
      if (!TypeConversionUtil.isAssignable(processorType, classType)) continue;
      PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      PsiClass aClass = resolveResult.getElement();
      if (aClass == null) continue;
      PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(processorClass, aClass, resolveResult.getSubstitutor());
      if (substitutor == null) continue;
      Map<PsiTypeParameter, PsiType> substitutionMap = substitutor.getSubstitutionMap();
      if (substitutionMap.isEmpty() || substitutionMap.containsValue(null)) {
        String text = JavaErrorBundle.message("raw.processor.type.not.allowed", type.getPresentableText());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(processor).descriptionAndTooltip(text);
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkTryResourceIsAutoCloseable(@NotNull PsiResourceListElement resource) {
    PsiType type = resource.getType();
    if (type == null) return null;

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(resource.getProject());
    PsiClassType autoCloseable = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, resource.getResolveScope());
    if (TypeConversionUtil.isAssignable(autoCloseable, type)) return null;
    if (IncompleteModelUtil.isIncompleteModel(resource) && IncompleteModelUtil.isPotentiallyConvertible(autoCloseable, type, resource)) return null;

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

  static HighlightInfo.Builder checkArrayInitializer(@NotNull PsiExpression initializer, @Nullable PsiType initializerType,
                                                     @NotNull PsiArrayInitializerExpression arrayInitializerExpression) {
    PsiType arrayType = arrayInitializerExpression.getType();
    if (!(arrayType instanceof PsiArrayType theArrayType)) return null;
    PsiType componentType = theArrayType.getComponentType();
    HighlightInfo.Builder info = checkArrayInitializerCompatibleTypes(initializer, initializerType, componentType);
    if (info != null) {
      PsiType sameType = JavaHighlightUtil.sameType(arrayInitializerExpression.getInitializers());
      VariableArrayTypeFix fix = sameType == null ? null : VariableArrayTypeFix.createFix(arrayInitializerExpression, sameType);
      if (fix != null) {
        info.registerFix(fix, null, null, null, null);
      }
    }
    return info;
  }

  private static HighlightInfo.Builder checkArrayInitializerCompatibleTypes(@NotNull PsiExpression initializer,
                                                                            @Nullable PsiType initializerType,
                                                                            @NotNull PsiType componentType) {
    if (initializerType == null) {
      if (IncompleteModelUtil.isIncompleteModel(initializer) && IncompleteModelUtil.mayHaveUnknownTypeDueToPendingReference(initializer)) {
        return null;
      }
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

  static HighlightInfo.Builder checkLocalClassReferencedFromAnotherSwitchBranch(@NotNull PsiJavaCodeReferenceElement ref,
                                                                                @NotNull PsiClass aClass) {
    if (!(aClass.getParent() instanceof PsiDeclarationStatement declarationStatement) ||
        !(declarationStatement.getParent() instanceof PsiCodeBlock codeBlock) ||
        !(codeBlock.getParent() instanceof PsiSwitchBlock)) {
      return null;
    }
    boolean classSeen = false;
    for (PsiStatement statement : codeBlock.getStatements()) {
      if (classSeen) {
        if (PsiTreeUtil.isAncestor(statement, ref, true)) break;
        if (statement instanceof PsiSwitchLabelStatement) {
          String description = JavaErrorBundle.message("local.class.referenced.from.other.switch.branch", formatClass(aClass));
          return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(ref).descriptionAndTooltip(description);
        }
      }
      else if (statement == declarationStatement) {
        classSeen = true;
      }
    }
    return null;
  }

  static void checkSwitchExpressionHasResult(@NotNull PsiSwitchExpression switchExpression,
                                             @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiCodeBlock switchBody = switchExpression.getBody();
    if (switchBody != null) {
      PsiStatement lastStatement = PsiTreeUtil.getPrevSiblingOfType(switchBody.getRBrace(), PsiStatement.class);
      boolean hasResult = false;
      if (lastStatement instanceof PsiSwitchLabeledRuleStatement) {
        boolean reported = false;
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
              HighlightInfo.Builder info =
                HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(target).descriptionAndTooltip(message);
              errorSink.accept(info);
              reported = true;
            }
            else if (!hasResult && hasYield(switchExpression, ruleBody)) {
              hasResult = true;
            }
          }
        }
        if (reported) {
          return;
        }
      }
      else {
        // previous statements may have no result as well, but in that case they fall through to the last one, which needs to be checked anyway
        if (lastStatement != null && ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
          PsiElement target =
            ObjectUtils.notNull(tryCast(switchExpression.getFirstChild(), PsiKeyword.class), switchExpression);
          String message = JavaErrorBundle.message("switch.expr.should.produce.result");
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(target).descriptionAndTooltip(message);
          errorSink.accept(info);
          return;
        }
        hasResult = hasYield(switchExpression, switchBody);
      }
      if (!hasResult) {
        PsiElement target = ObjectUtils.notNull(tryCast(switchExpression.getFirstChild(), PsiKeyword.class), switchExpression);
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(target)
          .descriptionAndTooltip(JavaErrorBundle.message("switch.expr.no.result"));
        errorSink.accept(info);
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
    String key = referencedField instanceof PsiEnumConstant
                 ? (isIllegalForwardReference ? "illegal.forward.reference.enum" : "illegal.self.reference.enum")
                 : (isIllegalForwardReference ? "illegal.forward.reference" : "illegal.self.reference");
    String description = JavaErrorBundle.message(key, referencedField.getName());
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
        if (IncompleteModelUtil.isIncompleteModel(containingFile)) {
          return null;
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
                                                                            @NotNull Function<? super PsiElement, ? extends PsiMethod> surroundingConstructor) {
    PsiMethod constructor = surroundingConstructor.apply(expression);
    if (constructor == null) {
      // not inside expression inside constructor
      return null;
    }
    PsiMethodCallExpression constructorCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
    if (constructorCall == null) {
      return null;
    }
    if (expression.getTextOffset() > constructorCall.getTextOffset() + constructorCall.getTextLength()) {
      return null;
    }
    // is in or before this() or super() call

    PsiClass referencedClass;
    String resolvedName;
    PsiType type;
    PsiElement parent = expression.getParent();
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

      boolean isSuperCall = JavaPsiConstructorUtil.isSuperConstructorCall(parent);
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
      if (resolved instanceof PsiField field) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) return null;
        LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);
        if (JavaFeature.STATEMENTS_BEFORE_SUPER.isSufficient(languageLevel) &&
            languageLevel != LanguageLevel.JDK_22_PREVIEW &&
            isOnSimpleAssignmentLeftHand(expression) &&
            field.getContainingClass() == PsiTreeUtil.getParentOfType(expression, PsiClass.class, true)) {
          return null;
        }
        resolvedName = 
          PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
        referencedClass = field.getContainingClass();
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
        if (expression instanceof PsiReferenceExpression) return null;
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

    PsiClass parentClass = constructor.getContainingClass();
    if (parentClass == null) {
      return null;
    }

    // references to private methods from the outer class are not calls to super methods
    // even if the outer class is the super class
    if (resolved instanceof PsiMember member && member.hasModifierProperty(PsiModifier.PRIVATE) && referencedClass != parentClass) {
      return null;
    }
    // field or method should be declared in this class or super
    if (!InheritanceUtil.isInheritorOrSelf(parentClass, referencedClass, true)) return null;
    // and point to our instance
    if (expression instanceof PsiReferenceExpression ref) {
      PsiExpression qualifier = ref.getQualifierExpression();
      if (!isThisOrSuperReference(qualifier, parentClass)) {
        return null;
      }
      else if (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
        if (((PsiQualifiedExpression)qualifier).getQualifier() != null) return null;
      }
    }

    if (expression instanceof PsiThisExpression || expression instanceof PsiSuperExpression) {
      if (referencedClass != parentClass) return null;
    }

    if (expression instanceof PsiJavaCodeReferenceElement) {
      if (!parentClass.equals(PsiTreeUtil.getParentOfType(expression, PsiClass.class)) &&
          PsiTreeUtil.getParentOfType(expression, PsiTypeElement.class) != null) {
        return null;
      }

      if (PsiTreeUtil.getParentOfType(expression, PsiClassObjectAccessExpression.class) != null) {
        return null;
      }

      if (parent instanceof PsiNewExpression newExpression &&
          newExpression.isArrayCreation() &&
          newExpression.getClassOrAnonymousClassReference() == expression) {
        return null;
      }
      if (parent instanceof PsiThisExpression || parent instanceof PsiSuperExpression) return null;
    }
    if (!(expression instanceof PsiThisExpression) && !(expression instanceof PsiSuperExpression) ||
        ((PsiQualifiedExpression)expression).getQualifier() == null) {
      PsiClass expressionClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class, true);
      while (expressionClass != null && parentClass != expressionClass) {
        if (InheritanceUtil.isInheritorOrSelf(expressionClass, referencedClass, true)) {
          return null;
        }
        expressionClass = PsiTreeUtil.getParentOfType(expressionClass, PsiClass.class, true);
      }
    }

    if (expression instanceof PsiThisExpression) {
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);
      if (JavaFeature.STATEMENTS_BEFORE_SUPER.isSufficient(languageLevel) && languageLevel != LanguageLevel.JDK_22_PREVIEW) {
        parent = PsiUtil.skipParenthesizedExprUp(parent);
        if (isOnSimpleAssignmentLeftHand(parent) &&
            parent instanceof PsiReferenceExpression ref &&
            ref.resolve() instanceof PsiField field &&
            field.getContainingClass() == PsiTreeUtil.getParentOfType(expression, PsiClass.class, true)) {
          return null;
        }
      }
    }
    HighlightInfo.Builder builder = createMemberReferencedError(resolvedName, expression.getTextRange(), resolved instanceof PsiMethod);
    if (expression instanceof PsiReferenceExpression ref && PsiUtil.isInnerClass(parentClass)) {
      String referenceName = ref.getReferenceName();
      PsiClass containingClass = parentClass.getContainingClass();
      LOG.assertTrue(containingClass != null);
      PsiField fieldInContainingClass = containingClass.findFieldByName(referenceName, true);
      if (fieldInContainingClass != null && ref.getQualifierExpression() == null) {
        builder.registerFix(new QualifyWithThisFix(containingClass, ref), null, null, null, null);
      }
    }

    return builder;
  }

  private static boolean isOnSimpleAssignmentLeftHand(@NotNull PsiElement expr) {
    PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class);
    return parent instanceof PsiAssignmentExpression assignment &&
           JavaTokenType.EQ == assignment.getOperationTokenType() &&
           PsiTreeUtil.isAncestor(assignment.getLExpression(), expr, false);
  }

  @NotNull
  private static HighlightInfo.Builder createMemberReferencedError(@NotNull String resolvedName, @NotNull TextRange textRange, boolean methodCall) {
    String description = methodCall
                         ? JavaErrorBundle.message("method.called.before.constructor.called", resolvedName)
                         : JavaErrorBundle.message("member.referenced.before.constructor.called", resolvedName);
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
      return createMemberReferencedError(aClass.getName() + ".this", range, false);
    }
    for (PsiMethod constructor : constructors) {
      PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
      if (!JavaPsiConstructorUtil.isSuperConstructorCall(call)) {
        return createMemberReferencedError(aClass.getName() + ".this", HighlightNamesUtil.getMethodDeclarationTextRange(constructor), false);
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
    String text = comment.getText();
    if (text.startsWith("/*") && !text.endsWith("*/")) {
      int start = comment.getTextRange().getEndOffset() - 1;
      int end = start + 1;
      String description = JavaErrorBundle.message("unclosed.comment");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(start, end).descriptionAndTooltip(description);
    }
    return null;
  }

  static void checkIllegalUnicodeEscapes(@NotNull PsiElement element, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    parseUnicodeEscapes(element.getText(), (start, end) -> {
      int offset = element.getTextOffset();
      errorSink.accept(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(offset + start, offset + end)
        .descriptionAndTooltip(JavaErrorBundle.message("illegal.unicode.escape")));
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
          result.append('\\').append(c);
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

  static void checkCatchTypeIsDisjoint(@NotNull PsiParameter parameter, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
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
          errorSink.accept(builder);
          break;
        }
      }
    }
  }


  static void checkExceptionAlreadyCaught(@NotNull PsiParameter parameter, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
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
          errorSink.accept(builder);
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
    PsiType baseLType = PsiUtil.convertAnonymousToBaseType(lType);
    PsiType baseRType = rType == null ? null : PsiUtil.convertAnonymousToBaseType(rType);
    boolean leftAnonymous = PsiUtil.resolveClassInClassTypeOnly(lType) instanceof PsiAnonymousClass;
    String styledReason = reason.isEmpty() ? "" :
                          String.format("<table><tr><td style=''padding-top: 10px; padding-left: 4px;''>%s</td></tr></table>", reason);
    IncompatibleTypesTooltipComposer tooltipComposer = (lTypeString, lTypeArguments, rTypeString, rTypeArguments) -> 
      JavaErrorBundle.message("incompatible.types.html.tooltip",
                              lTypeString, lTypeArguments,
                              rTypeString, rTypeArguments,
                              styledReason, "#" + ColorUtil.toHex(UIUtil.getContextHelpForeground()));
    String toolTip = createIncompatibleTypesTooltip(leftAnonymous ? lType : baseLType, leftAnonymous ? rType : baseRType, tooltipComposer);

    String lTypeString = JavaHighlightUtil.formatType(leftAnonymous ? lType : baseLType);
    String rTypeString = JavaHighlightUtil.formatType(leftAnonymous ? rType : baseRType);
    String description = JavaErrorBundle.message("incompatible.types", lTypeString, rTypeString);
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

  static HighlightInfo.Builder checkExtraSemicolonBetweenImportStatements(@NotNull PsiJavaToken token,
                                                                          IElementType type,
                                                                          @NotNull LanguageLevel level) {
    if (type == JavaTokenType.SEMICOLON && level.isAtLeast(LanguageLevel.JDK_21) && PsiUtil.isFollowedByImport(token)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(token)
        .registerFix(QuickFixFactory.getInstance().createDeleteFix(token), null, null, null, null)
        .descriptionAndTooltip(JavaErrorBundle.message("error.extra.semicolons.between.import.statements.not.allowed"));
    }
    return null;
  }

  /**
   * @param elementToHighlight element to attach the highlighting
   * @return HighlightInfo builder that adds a pending reference highlight
   */
  static HighlightInfo.@NotNull Builder getPendingReferenceHighlightInfo(@NotNull PsiElement elementToHighlight) {
    return HighlightInfo.newHighlightInfo(HighlightInfoType.PENDING_REFERENCE).range(elementToHighlight)
      .descriptionAndTooltip(JavaErrorBundle.message("incomplete.project.state.pending.reference"));
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
    TypeData lTypeData = typeData(lType);
    TypeData rTypeData = typeData(rType);
    PsiTypeParameter[] lTypeParams = lTypeData.typeParameters();
    PsiTypeParameter[] rTypeParams = rTypeData.typeParameters();

    int typeParamColumns = Math.max(lTypeParams.length, rTypeParams.length);
    boolean skipColumns = consumer.skipTypeArgsColumns();
    StringBuilder requiredRow = new StringBuilder();
    StringBuilder foundRow = new StringBuilder();
    for (int i = 0; i < typeParamColumns; i++) {
      PsiTypeParameter lTypeParameter = i >= lTypeParams.length ? null : lTypeParams[i];
      PsiTypeParameter rTypeParameter = i >= rTypeParams.length ? null : rTypeParams[i];
      PsiType lSubstitutedType = lTypeParameter == null ? null : lTypeData.substitutor().substitute(lTypeParameter);
      PsiType rSubstitutedType = rTypeParameter == null ? null : rTypeData.substitutor().substitute(rTypeParameter);
      boolean matches = lSubstitutedType == rSubstitutedType ||
                        lSubstitutedType != null &&
                        rSubstitutedType != null &&
                        TypeConversionUtil.typesAgree(lSubstitutedType, rSubstitutedType, true);
      String openBrace = i == 0 ? "&lt;" : "";
      String closeBrace = i == typeParamColumns - 1 ? "&gt;" : ",";
      boolean showShortType = showShortType(lSubstitutedType, rSubstitutedType);

      requiredRow.append(skipColumns ? "" : "<td style='padding: 0px 0px 8px 0px;'>")
        .append(lTypeParams.length == 0 ? "" : openBrace)
        .append(redIfNotMatch(lSubstitutedType, true, showShortType))
        .append(i < lTypeParams.length ? closeBrace : "")
        .append(skipColumns ? "" : "</td>");

      foundRow.append(skipColumns ? "" : "<td style='padding: 0px 0px 0px 0px;'>")
        .append(rTypeParams.length == 0 ? "" : openBrace)
        .append(redIfNotMatch(rSubstitutedType, matches, showShortType))
        .append(i < rTypeParams.length ? closeBrace : "")
        .append(skipColumns ? "" : "</td>");
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

    return lType != null && rType != null &&
           (!lType.getPresentableText().equals(rType.getPresentableText()) ||
            lType.getCanonicalText().equals(rType.getCanonicalText()));
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

  private record TypeData(@NotNull PsiTypeParameter @NotNull [] typeParameters, @NotNull PsiSubstitutor substitutor) {}
  @NotNull
  private static TypeData typeData(PsiType type) {
    PsiTypeParameter[] parameters;
    PsiSubstitutor substitutor;
    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      substitutor = resolveResult.getSubstitutor();
      PsiClass psiClass = resolveResult.getElement();
      parameters = psiClass == null || ((PsiClassType)type).isRaw() ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
    }
    else {
      substitutor = PsiSubstitutor.EMPTY;
      parameters = PsiTypeParameter.EMPTY_ARRAY;
    }
    return new TypeData(parameters, substitutor);
  }

  @NotNull
  static @NlsSafe HtmlChunk redIfNotMatch(@Nullable PsiType type, boolean matches, boolean shortType) {
    if (type == null) return HtmlChunk.empty();
    String typeText;
    if (shortType || type instanceof PsiCapturedWildcardType) {
      typeText = PsiUtil.resolveClassInClassTypeOnly(type) instanceof PsiAnonymousClass
                 ? ANONYMOUS + type.getPresentableText()
                 : type.getPresentableText();
    }
    else {
      typeText = type.getCanonicalText();
    }
    Color color = matches
                  ? ExperimentalUI.isNewUI() ? JBUI.CurrentTheme.Editor.Tooltip.FOREGROUND : UIUtil.getToolTipForeground()
                  : NamedColorUtil.getErrorForeground();
    return HtmlChunk.tag("font").attr("color", ColorUtil.toHtmlColor(color)).addText(typeText);
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
      if (IncompleteModelUtil.isIncompleteModel(context) && IncompleteModelUtil.isPotentiallyConvertible(throwable, type, context)) return null;
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

    if (refParent instanceof PsiReferenceExpression && refParent.getParent() instanceof PsiMethodCallExpression granny) {
      PsiReferenceExpression referenceToMethod = granny.getMethodExpression();
      PsiExpression qualifierExpression = referenceToMethod.getQualifierExpression();
      if (qualifierExpression == ref && resolved != null && !(resolved instanceof PsiClass) && !(resolved instanceof PsiVariable)) {
        String message = JavaErrorBundle.message("qualifier.must.be.expression");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(qualifierExpression).descriptionAndTooltip(message);
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
        if (ref instanceof PsiMethodReferenceExpression methodRef &&
            IncompleteModelUtil.isIncompleteModel(ref) &&
            IncompleteModelUtil.isUnresolvedClassType(methodRef.getFunctionalInterfaceType())) {
          return null;
        }
        String t1 = format(Objects.requireNonNull(results[0].getElement()));
        String t2 = format(Objects.requireNonNull(results[1].getElement()));
        description = JavaErrorBundle.message("ambiguous.reference", refName.getText(), t1, t2);
      }
      else {
        description = JavaErrorBundle.message("cannot.resolve.symbol", refName.getText());
        boolean definitelyIncorrect = false;
        if (ref instanceof PsiReferenceExpression expression) {
          PsiExpression qualifierExpression = expression.getQualifierExpression();
          if (qualifierExpression != null &&
              qualifierExpression.getType() instanceof PsiPrimitiveType primitiveType &&
              !primitiveType.equals(PsiTypes.nullType()) && !primitiveType.equals(PsiTypes.voidType())) {
            description = JavaErrorBundle.message("cannot.access.member.on.type", qualifierExpression.getText(),
                                                  primitiveType.getPresentableText(false));
            definitelyIncorrect = true;
          }
        } else if (!JavaImplicitClassIndex.getInstance().getElements(ref.getQualifiedName(), ref.getProject(), ref.getResolveScope()).isEmpty()) {
          description = JavaErrorBundle.message("implicit.class.can.not.be.referenced", ref.getText());
          definitelyIncorrect = true;
        }
        if (!definitelyIncorrect && IncompleteModelUtil.isIncompleteModel(containingFile) && IncompleteModelUtil.canBePendingReference(ref)) {
          return getPendingReferenceHighlightInfo(refName);
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

    final ErrorWithFixes moduleProblem = checkModuleAccess(resolved, ref, result);
    if (!skipValidityChecks && !(result.isValidResult() && moduleProblem == null)) {
      if (moduleProblem != null) {
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(findPackagePrefix(ref))
          .descriptionAndTooltip(moduleProblem.message);
        moduleProblem.fixes.forEach(fix -> info.registerFix(fix, List.of(), null, null, null));
        return info;
      }

      if (!result.isAccessible()) {
        @Nls String description = accessProblemDescription(ref, resolved, result);
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refName).descriptionAndTooltip(description);
        if (result.isStaticsScopeCorrect() && resolved instanceof PsiJvmMember) {
          HighlightFixUtil.registerAccessQuickFixAction(info, refName.getTextRange(), (PsiJvmMember)resolved, ref, result.getCurrentFileResolveScope(), null);
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
    if (CommonClassNames.SERIAL_VERSION_UID_FIELD_NAME.equals(name)) {
      if (!PsiTypes.longType().equals(field.getType())) return false;
    }
    else if (SERIAL_PERSISTENT_FIELDS_FIELD_NAME.equals(name)) {
      if (!field.hasModifierProperty(PsiModifier.PRIVATE)) return false;
      if (!(field.getType() instanceof PsiArrayType arrayType) || arrayType.getArrayDimensions() != 1) return false;
      PsiType componentType = arrayType.getComponentType();
      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(componentType);
      if (aClass != null && !"java.io.ObjectStreamField".equals(aClass.getQualifiedName())) return false;
    }
    else {
      return false;
    }
    if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) return false;
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
  private static LanguageLevel getApplicableLevel(@NotNull PsiFile file, @NotNull JavaFeature feature) {
    LanguageLevel standardLevel = feature.getStandardLevel();
    LanguageLevel featureLevel = feature.getMinimumLevel();
    if (featureLevel.isPreview()) {
      JavaSdkVersion sdkVersion = JavaSdkVersionUtil.getJavaSdkVersion(file);
      if (sdkVersion != null) {
        if (standardLevel != null && sdkVersion.isAtLeast(JavaSdkVersion.fromLanguageLevel(standardLevel))) {
          return standardLevel;
        }
        LanguageLevel previewLevel = sdkVersion.getMaxLanguageLevel().getPreviewLevel();
        if (previewLevel != null && previewLevel.isAtLeast(featureLevel)) {
          return previewLevel;
        }
      }
    }
    return featureLevel;
  }

  @Nullable
  static HighlightInfo.Builder checkFeature(@NotNull PsiElement element,
                                           @NotNull JavaFeature feature,
                                           @NotNull LanguageLevel level,
                                           @NotNull PsiFile file) {
    return checkFeature(element, feature, level, file, null, HighlightInfoType.ERROR);
  }

  @Nullable
  static HighlightInfo.Builder checkFeature(@NotNull PsiElement element,
                                            @NotNull JavaFeature feature,
                                            @NotNull LanguageLevel level,
                                            @NotNull PsiFile file,
                                            @Nullable @NlsContexts.DetailedDescription String message,
                                            @NotNull HighlightInfoType highlightInfoType) {
    if (!feature.isSufficient(level)) {
      message = message == null ? getUnsupportedFeatureMessage(feature, level, file) : message;
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(highlightInfoType).range(element).descriptionAndTooltip(message);
      registerIncreaseLanguageLevelFixes(file, feature, info);
      return info;
    }

    return null;
  }

  static HighlightInfo.Builder checkFeature(@NotNull TextRange range,
                                            @NotNull JavaFeature feature,
                                            @NotNull LanguageLevel level,
                                            @NotNull PsiFile file) {
    if (!feature.isSufficient(level)) {
      String message = getUnsupportedFeatureMessage(feature, level, file);
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message);
      registerIncreaseLanguageLevelFixes(file, feature, info);
      return info;
    }

    return null;
  }

  public static void registerIncreaseLanguageLevelFixes(@NotNull PsiElement element,
                                                        @NotNull JavaFeature feature,
                                                        HighlightInfo.Builder info) {
    if (info == null) return;
    List<IntentionAction> registrar = new ArrayList<>();
    registerIncreaseLanguageLevelFixes(element, feature, registrar);
    for (IntentionAction action : registrar) {
      info.registerFix(action, null, null, null, null);
    }
  }

  public static void registerIncreaseLanguageLevelFixes(@NotNull PsiElement element,
                                                        @NotNull JavaFeature feature,
                                                        @NotNull List<? super IntentionAction> registrar) {
    if (PsiUtil.isAvailable(feature, element)) return;
    if (feature.isLimited()) return; //no reason for applying it because it can be outdated
    LanguageLevel applicableLevel = getApplicableLevel(element.getContainingFile(), feature);
    if (applicableLevel == LanguageLevel.JDK_X) return; // do not suggest to use experimental level
    registrar.add(getFixFactory().createIncreaseLanguageLevelFix(applicableLevel));
    registrar.add(getFixFactory().createUpgradeSdkFor(applicableLevel));
    registrar.add(getFixFactory().createShowModulePropertiesFix(element));
  }

  private static @NotNull @NlsContexts.DetailedDescription String getUnsupportedFeatureMessage(@NotNull JavaFeature feature,
                                                                                               @NotNull LanguageLevel level,
                                                                                               @NotNull PsiFile file) {
    String name = feature.getFeatureName();
    String version = JavaSdkVersion.fromLanguageLevel(level).getDescription();
    String message = JavaErrorBundle.message("insufficient.language.level", name, version);

    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module != null) {
      LanguageLevel moduleLanguageLevel = LanguageLevelUtil.getEffectiveLanguageLevel(module);
      if (moduleLanguageLevel.isAtLeast(feature.getMinimumLevel()) && !feature.isLimited()) {
        for (FilePropertyPusher<?> pusher : FilePropertyPusher.EP_NAME.getExtensionList()) {
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
