// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ContainerProvider;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.JavaModuleSystemEx;
import com.intellij.codeInsight.JavaModuleSystemEx.ErrorWithFixes;
import com.intellij.codeInsight.UnhandledExceptions;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater;
import com.intellij.codeInspection.dataFlow.fix.RedundantInstanceofFix;
import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModCommandAction;
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
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ImplicitClassSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.NewUI;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.util.ObjectUtils.tryCast;

// generates HighlightInfoType.ERROR-like HighlightInfos
public final class HighlightUtil {
  private static final Logger LOG = Logger.getInstance(HighlightUtil.class);

  private static final @NlsSafe String ANONYMOUS = "anonymous ";

  private HighlightUtil() { }

  private static @NotNull QuickFixFactory getFixFactory() {
    return QuickFixFactory.getInstance();
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
      if (((operandIsPrimitive || checkIsPrimitive) && !primitiveInPatternsEnabled) && convertible) {
        HighlightInfo.Builder infoFeature =
          checkFeature(expression, JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS,
                       PsiUtil.getLanguageLevel(expression), expression.getContainingFile());
        if (infoFeature != null) {
          info = infoFeature;
        }
      }
      if (checkIsPrimitive) {
        IntentionAction action = getFixFactory().createReplacePrimitiveWithBoxedTypeAction(operandType, typeElement);
        if (action != null) {
          info.registerFix(action, null, null, null, null);
        }
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
        if (conjType instanceof PsiClassType classType) {
          PsiClass aClass = classType.resolve();
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
      HighlightFixUtil.registerChangeVariableTypeFixes(initializer, lType, null, asConsumer(highlightInfo));
    }
    return highlightInfo;
  }

  static HighlightInfo.Builder checkRestrictedIdentifierReference(@NotNull PsiJavaCodeReferenceElement ref,
                                                          @NotNull PsiClass resolved,
                                                          @NotNull LanguageLevel languageLevel) {
    String name = resolved.getName();
    if (PsiTypesUtil.isRestrictedIdentifier(name, languageLevel)) {
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
      if (parent instanceof PsiDeclarationStatement statement && statement.getDeclaredElements().length > 1) {
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
    if (variable instanceof PsiLocalVariable localVariable) {
      PsiExpression initializer = variable.getInitializer();
      if (initializer == null) {
        if (PsiUtilCore.hasErrorElementChild(variable)) return null;
        String message = JavaErrorBundle.message("lvti.no.initializer");
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(message).range(typeElement);
        HighlightFixUtil.registerSpecifyVarTypeFix(localVariable, info);
        return info;
      }
      PsiExpression deparen = PsiUtil.skipParenthesizedExprDown(initializer);
      if (deparen instanceof PsiFunctionalExpression) {
        boolean lambda = deparen instanceof PsiLambdaExpression;
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
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .descriptionAndTooltip(JavaErrorBundle.message("lvti.null"))
            .range(typeElement);
        HighlightFixUtil.registerSpecifyVarTypeFix(localVariable, info);
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
    AddTypeArgumentsConditionalFix.register(asConsumer(highlightInfo), expression, lType);
    if (expression != null) {
      AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(asConsumer(highlightInfo), expression, lType, rType);
      if (!(expression.getParent() instanceof PsiConditionalExpression && PsiTypes.voidType().equals(lType))) {
        IntentionAction fix = HighlightFixUtil.createChangeReturnTypeFix(expression, lType);
        if (fix != null) {
          highlightInfo.registerFix(fix, null, null, null, null);
        }
      }
    }
    ModCommandAction fix = ChangeNewOperatorTypeFix.createFix(expression, lType);
    if (fix != null) {
      highlightInfo.registerFix(fix, null, null, null, null);
    }
    return highlightInfo;
  }

  static @NotNull Consumer<CommonIntentionAction> asConsumer(@Nullable HighlightInfo.Builder highlightInfo) {
    if (highlightInfo == null) {
      return fix -> {};
    }
    return fix -> highlightInfo.registerFix(fix.asIntention(), null, null, null, null);
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
            HighlightFixUtil.registerChangeParameterClassFix(returnType, valueType, asConsumer(errorResult));
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

  public static @NotNull @NlsContexts.DetailedDescription String getUnhandledExceptionsDescriptor(@NotNull Collection<? extends PsiClassType> unhandled) {
    return JavaErrorBundle.message("unhandled.exceptions", formatTypes(unhandled), unhandled.size());
  }

  private static @NotNull String formatTypes(@NotNull Collection<? extends PsiClassType> unhandled) {
    return StringUtil.join(unhandled, JavaHighlightUtil::formatType, ", ");
  }

  public static HighlightInfo.Builder checkVariableAlreadyDefined(@NotNull PsiVariable variable) {
    if (variable instanceof ExternallyDefinedPsiElement || variable.isUnnamed()) return null;
    PsiVariable oldVariable = null;
    PsiElement declarationScope = null;
    if (variable instanceof PsiLocalVariable || variable instanceof PsiPatternVariable ||
        variable instanceof PsiParameter parameter &&
        ((declarationScope = parameter.getDeclarationScope()) instanceof PsiCatchSection ||
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
      else if (variable instanceof PsiPatternVariable patternVariable) {
        oldVariable = findSamePatternVariableInBranches(patternVariable);
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
      if (variable instanceof PsiLocalVariable localVariable) {
        IntentionAction action = getFixFactory().createReuseVariableDeclarationFix(localVariable);
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
      if (parent instanceof PsiPrefixExpression expression && expression.getOperationTokenType().equals(JavaTokenType.EXCL)) {
        hint = hint.invert();
        continue;
      }
      if (parent instanceof PsiPolyadicExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
          PatternResolveState targetHint = PatternResolveState.fromBoolean(tokenType.equals(JavaTokenType.OROR));
          if (hint == targetHint) {
            for (PsiExpression operand : expression.getOperands()) {
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
      if (child instanceof PsiVariable psiVariable) {
        if (child.equals(variable)) continue;
        if (Objects.equals(variable.getName(), psiVariable.getName())) {
          return psiVariable;
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

  public static @NotNull @NlsSafe String formatClass(@NotNull PsiClass aClass) {
    return formatClass(aClass, true);
  }

  public static @NotNull String formatClass(@NotNull PsiClass aClass, boolean fqn) {
    return PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_ANONYMOUS_CLASS_VERBOSE | (fqn ? PsiFormatUtilBase.SHOW_FQ_NAME : 0));
  }

  private static @NotNull String formatField(@NotNull PsiField field) {
    return PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
  }

  static HighlightInfo.Builder checkUnhandledCloserExceptions(@NotNull PsiResourceListElement resource) {
    List<PsiClassType> unhandled = ExceptionUtil.getUnhandledCloserExceptions(resource, null);
    if (unhandled.isEmpty()) return null;

    HighlightInfoType highlightType = getUnhandledExceptionHighlightType(resource);
    if (highlightType == null) return null;

    String description = JavaErrorBundle.message("unhandled.close.exceptions", formatTypes(unhandled), unhandled.size(),
                              JavaErrorBundle.message("auto.closeable.resource"));
    HighlightInfo.Builder highlight = HighlightInfo.newHighlightInfo(highlightType).range(resource).descriptionAndTooltip(description);
    HighlightFixUtil.registerUnhandledExceptionFixes(resource, asConsumer(highlight));
    return highlight;
  }

  private static @Nullable HighlightInfoType getUnhandledExceptionHighlightType(@NotNull PsiElement element) {
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
    if (statement instanceof PsiExpressionStatement expressionStatement) {
      List<IntentionAction> registrar = new ArrayList<>();
      HighlightFixUtil.registerFixesForExpressionStatement(statement, registrar);
      QuickFixAction.registerQuickFixActions(error, null, registrar);
      PsiElement parent = expressionStatement.getParent();
      if (parent instanceof PsiCodeBlock ||
          parent instanceof PsiIfStatement ||
          parent instanceof PsiLoopStatement loop && loop.getBody() == expressionStatement) {
        IntentionAction action = PriorityIntentionActionWrapper
          .lowPriority(getFixFactory().createDeleteSideEffectAwareFix(expressionStatement));
        error.registerFix(action, null, null, null, null);
      }
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
    else if (parent instanceof PsiLocalVariable localVariable) {
      HighlightFixUtil.registerChangeVariableTypeFixes(localVariable, expectedType, null, info);
    }
    else if (parent instanceof PsiAssignmentExpression assignmentExpression) {
      HighlightFixUtil.registerChangeVariableTypeFixes(assignmentExpression.getLExpression(), expectedType, null, asConsumer(info));
    }
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

  static @NotNull @NlsContexts.DetailedDescription String staticContextProblemDescription(@NotNull PsiElement refElement) {
    String type = JavaElementKind.fromElement(refElement).lessDescriptive().subject();
    String name = HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY);
    return JavaErrorBundle.message("non.static.symbol.referenced.from.static.context", type, name);
  }

  static @NotNull @NlsContexts.DetailedDescription String accessProblemDescription(@NotNull PsiElement ref,
                                                                                   @NotNull PsiElement resolved,
                                                                                   @NotNull JavaResolveResult result) {
    return accessProblemDescriptionAndFixes(ref, resolved, result).first;
  }

  static @NotNull Pair<@Nls String, List<IntentionAction>> accessProblemDescriptionAndFixes(@NotNull PsiElement ref,
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

    PsiClass packageLocalClass = JavaPsiModifierUtil.getPackageLocalClassInTheMiddle(ref);
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

  static @Nullable @Nls ErrorWithFixes checkModuleAccess(@NotNull PsiElement resolved, @NotNull PsiElement ref, @NotNull JavaResolveResult result) {
    PsiElement refElement = resolved;
    PsiClass packageLocalClass = JavaPsiModifierUtil.getPackageLocalClassInTheMiddle(ref);
    if (packageLocalClass != null) {
      refElement = packageLocalClass;
    }

    String symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());
    String containerName = (resolved instanceof PsiModifierListOwner modifierListOwner)
                           ? getContainerName(modifierListOwner, result.getSubstitutor())
                           : null;
    return checkModuleAccess(resolved, ref, symbolName, containerName);
  }

  private static @Nullable @Nls ErrorWithFixes checkModuleAccess(@NotNull PsiElement target,
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
    if (target instanceof PsiClass psiClass) return system.isAccessible(psiClass, place);
    if (target instanceof PsiPackage psiPackage) return system.isAccessible(psiPackage.getQualifiedName(), null, place);
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


  static void checkSwitchExpressionHasResult(@NotNull PsiSwitchExpression switchExpression,
                                             @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiCodeBlock switchBody = switchExpression.getBody();
    if (switchBody != null) {
      PsiStatement lastStatement = PsiTreeUtil.getPrevSiblingOfType(switchBody.getRBrace(), PsiStatement.class);
      boolean hasResult = false;
      if (lastStatement instanceof PsiSwitchLabeledRuleStatement rule) {
        boolean reported = false;
        for (;
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


  static HighlightInfo.Builder checkIllegalType(@NotNull PsiTypeElement typeElement, @NotNull PsiFile containingFile) {
    PsiElement parent = typeElement.getParent();
    if (parent instanceof PsiTypeElement) return null;

    if (PsiUtil.isInsideJavadocComment(typeElement)) return null;

    PsiType type = typeElement.getType();
    PsiType componentType = type.getDeepComponentType();
    if (componentType instanceof PsiClassType) {
      PsiClass aClass = PsiUtil.resolveClassInType(componentType);
      if (aClass == null) {
        if (typeElement.isInferredType() && parent instanceof PsiLocalVariable localVariable) {
          PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(localVariable.getInitializer());
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
    if (expression instanceof PsiJavaCodeReferenceElement referenceElement) {
      // redirected ctr
      if (PsiKeyword.THIS.equals(referenceElement.getReferenceName())
          && resolved instanceof PsiMethod psiMethod
          && psiMethod.isConstructor()) {
        return null;
      }
      PsiElement qualifier = referenceElement.getQualifier();
      type = qualifier instanceof PsiExpression psiExpression ? psiExpression.getType() : null;
      referencedClass = PsiUtil.resolveClassInType(type);

      boolean isSuperCall = JavaPsiConstructorUtil.isSuperConstructorCall(parent);
      if (resolved == null && isSuperCall) {
        if (qualifier instanceof PsiReferenceExpression referenceExpression) {
          resolved = referenceExpression.resolve();
          expression = qualifier;
          type = referenceExpression.getType();
          referencedClass = PsiUtil.resolveClassInType(type);
        }
        else if (qualifier == null) {
          resolved = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiMember.class);
          if (resolved instanceof PsiMethod psiMethod) {
            referencedClass = psiMethod.getContainingClass();
          }
        }
        else if (qualifier instanceof PsiThisExpression thisExpression) {
          referencedClass = PsiUtil.resolveClassInType(thisExpression.getType());
        }
      }
      if (resolved instanceof PsiField field) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) return null;
        LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);
        if (JavaFeature.STATEMENTS_BEFORE_SUPER.isSufficient(languageLevel) &&
            languageLevel != LanguageLevel.JDK_22_PREVIEW &&
            isOnSimpleAssignmentLeftHand(expression) &&
            field.getContainingClass() == PsiTreeUtil.getParentOfType(expression, PsiClass.class, PsiLambdaExpression.class)) {
          if (field.hasInitializer()) {
            String fieldName = PsiFormatUtil.formatVariable(
              field, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
            String description = JavaErrorBundle.message("assign.initialized.field.before.constructor.call", fieldName);
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
          }
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
    // even if the outer class is the superclass
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
            field.getContainingClass() == PsiTreeUtil.getParentOfType(expression, PsiClass.class, PsiLambdaExpression.class)) {
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

  private static @NotNull HighlightInfo.Builder createMemberReferencedError(@NotNull String resolvedName, @NotNull TextRange textRange, boolean methodCall) {
    String description = methodCall
                         ? JavaErrorBundle.message("method.called.before.constructor.called", resolvedName)
                         : JavaErrorBundle.message("member.referenced.before.constructor.called", resolvedName);
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
  }

  private static boolean isThisOrSuperReference(@Nullable PsiExpression qualifierExpression, @NotNull PsiClass aClass) {
    if (qualifierExpression == null) return true;
    if (!(qualifierExpression instanceof PsiQualifiedExpression expression)) return false;
    PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
    if (qualifier == null) return true;
    PsiElement resolved = qualifier.resolve();
    return resolved instanceof PsiClass && InheritanceUtil.isInheritorOrSelf(aClass, (PsiClass)resolved, true);
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

  static @NotNull HighlightInfo.Builder createIncompatibleTypeHighlightInfo(@NotNull PsiType lType,
                                                                            @Nullable PsiType rType,
                                                                            @NotNull TextRange textRange,
                                                                            int navigationShift) {
    return createIncompatibleTypeHighlightInfo(lType, rType, textRange, navigationShift, getReasonForIncompatibleTypes(rType));
  }

  static @NotNull HighlightInfo.Builder createIncompatibleTypeHighlightInfo(@NotNull PsiType lType,
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

  static @NotNull @NlsContexts.Tooltip String createIncompatibleTypesTooltip(PsiType lType,
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
    PsiType lRawType = lType instanceof PsiClassType classType ? classType.rawType() : lType;
    PsiType rRawType = rType instanceof PsiClassType classType ? classType.rawType() : rType;
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
    if (rType instanceof PsiMethodReferenceType referenceType) {
      JavaResolveResult[] results = referenceType.getExpression().multiResolve(false);
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
  private static @NotNull TypeData typeData(PsiType type) {
    PsiTypeParameter[] parameters;
    PsiSubstitutor substitutor;
    if (type instanceof PsiClassType classType) {
      PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      substitutor = resolveResult.getSubstitutor();
      PsiClass psiClass = resolveResult.getElement();
      parameters = psiClass == null || classType.isRaw() ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
    }
    else {
      substitutor = PsiSubstitutor.EMPTY;
      parameters = PsiTypeParameter.EMPTY_ARRAY;
    }
    return new TypeData(parameters, substitutor);
  }

  static @NotNull @NlsSafe HtmlChunk redIfNotMatch(@Nullable PsiType type, boolean matches, boolean shortType) {
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
                  ? NewUI.isEnabled() ? JBUI.CurrentTheme.Editor.Tooltip.FOREGROUND : UIUtil.getToolTipForeground()
                  : NamedColorUtil.getErrorForeground();
    return HtmlChunk.tag("font").attr("color", ColorUtil.toHtmlColor(color)).addText(typeText);
  }


  static HighlightInfo.Builder checkSingleImportClassConflict(@NotNull PsiImportStatement statement,
                                                              @NotNull Map<String, Pair<PsiImportStaticReferenceElement, PsiClass>> importedClasses,
                                                              @NotNull PsiFile containingFile) {
    if (statement.isOnDemand()) return null;
    PsiElement element = statement.resolve();
    if (element instanceof PsiClass psiClass) {
      String name = psiClass.getName();
      Pair<PsiImportStaticReferenceElement, PsiClass> imported = importedClasses.get(name);
      PsiClass importedClass = Pair.getSecond(imported);
      if (importedClass != null && !containingFile.getManager().areElementsEquivalent(importedClass, element)) {
        String description = JavaErrorBundle.message("single.import.class.conflict", formatClass(importedClass));
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(description);
      }
      importedClasses.put(name, Pair.pair(null, psiClass));
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

      //do not highlight module keyword if the statement is not complete
      //see com.intellij.lang.java.parser.BasicFileParser.parseImportStatement
      if (PsiKeyword.MODULE.equals(ref.getText()) && refParent instanceof PsiImportStatement &&
          PsiUtil.isAvailable(JavaFeature.MODULE_IMPORT_DECLARATIONS, ref)) {
        PsiElement importKeywordExpected = PsiTreeUtil.skipWhitespacesAndCommentsBackward(ref);
        PsiElement errorElementExpected = PsiTreeUtil.skipWhitespacesAndCommentsForward(ref);
        if (importKeywordExpected instanceof PsiKeyword keyword &&
            keyword.textMatches(PsiKeyword.IMPORT) &&
            errorElementExpected instanceof PsiErrorElement errorElement &&
            JavaPsiBundle.message("expected.identifier.or.semicolon").equals(errorElement.getErrorDescription())) {
          return null;
        }
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
          if (qualifierExpression != null) {
            PsiType type = qualifierExpression.getType();
            if (type instanceof PsiPrimitiveType primitiveType && !primitiveType.equals(PsiTypes.nullType())) {
              if (PsiTypes.voidType().equals(primitiveType) &&
                  PsiUtil.deparenthesizeExpression(qualifierExpression) instanceof PsiReferenceExpression) {
                return null;
              }
              description = JavaErrorBundle.message("cannot.access.member.on.type", primitiveType.getPresentableText(false));
              definitelyIncorrect = true;
            }
            else if (type instanceof PsiClassType t && t.resolve() == null || PsiTypes.nullType().equals(type)) {
              return null;
            }
          }
        }
        else if (ImplicitClassSearch.search(ref.getQualifiedName(), ref.getProject(), ref.getResolveScope()).findFirst() != null) {
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
          HighlightFixUtil.registerAccessQuickFixAction(asConsumer(info), (PsiJvmMember)resolved, ref, result.getCurrentFileResolveScope());
          if (ref instanceof PsiReferenceExpression expression) {
            IntentionAction action = getFixFactory().createRenameWrongRefFix(expression);
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
        if (ref instanceof PsiReferenceExpression expression) {
          IntentionAction action = getFixFactory().createRenameWrongRefFix(expression);
          info.registerFix(action, null, null, null, null);
        }
        return info;
      }
    }

    if ((resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) && !(resolved instanceof ImplicitVariable)) {
      return HighlightControlFlowUtil.checkVariableMustBeFinal((PsiVariable)resolved, ref, languageLevel);
    }

    if (resolved instanceof PsiClass psiClass &&
        psiClass.getContainingClass() == null &&
        PsiUtil.isFromDefaultPackage(resolved) &&
        (PsiTreeUtil.getParentOfType(ref, PsiImportStatementBase.class) != null ||
         PsiUtil.isModuleFile(containingFile) ||
         !PsiUtil.isFromDefaultPackage(containingFile))) {
      String description = JavaErrorBundle.message("class.in.default.package", psiClass.getName());
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

  private static @NotNull PsiElement findPackagePrefix(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement candidate = ref;
    while (candidate instanceof PsiJavaCodeReferenceElement element) {
      if (element.resolve() instanceof PsiPackage) return candidate;
      candidate = element.getQualifier();
    }
    return ref;
  }

  static @NlsSafe @NotNull String format(@NotNull PsiElement element) {
    if (element instanceof PsiClass psiClass) return formatClass(psiClass);
    if (element instanceof PsiMethod psiMethod) return JavaHighlightUtil.formatMethod(psiMethod);
    if (element instanceof PsiField psiField) return formatField(psiField);
    if (element instanceof PsiLabeledStatement statement) return statement.getName() + ':';
    return ElementDescriptionUtil.getElementDescription(element, HighlightUsagesDescriptionLocation.INSTANCE);
  }

  private static @NotNull PsiJavaCodeReferenceElement getOuterReferenceParent(@NotNull PsiJavaCodeReferenceElement ref) {
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
        if (!(refGrandParent instanceof PsiTypeParameter)) {
          if (referenceList.equals(parentClass.getImplementsList()) || referenceList.equals(parentClass.getExtendsList())) {
            builder = HighlightClassUtil.checkExtendsSealedClass(parentClass, aClass, ref);
          }
        }
      }
    }
    return builder;
  }

  static HighlightInfo.Builder checkClassReferenceAfterQualifier(@NotNull PsiReferenceExpression expression, @Nullable PsiElement resolved) {
    if (!(resolved instanceof PsiClass)) return null;
    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier == null) return null;
    if (qualifier instanceof PsiReferenceExpression qExpression) {
      PsiElement qualifierResolved = qExpression.resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) return null;

      if (qualifierResolved == null) {
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

  private static @NotNull LanguageLevel getApplicableLevel(@NotNull PsiFile file, @NotNull JavaFeature feature) {
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

  static @Nullable HighlightInfo.Builder checkFeature(@NotNull PsiElement element,
                                                      @NotNull JavaFeature feature,
                                                      @NotNull LanguageLevel level,
                                                      @NotNull PsiFile file) {
    return checkFeature(element, feature, level, file, null, HighlightInfoType.ERROR);
  }

  static @Nullable HighlightInfo.Builder checkFeature(@NotNull PsiElement element,
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

  public static void registerIncreaseLanguageLevelFixes(@NotNull PsiElement element,
                                                        @NotNull JavaFeature feature,
                                                        HighlightInfo.Builder info) {
    if (info == null) return;
    for (CommonIntentionAction action : getIncreaseLanguageLevelFixes(element, feature)) {
      info.registerFix(action.asIntention(), null, null, null, null);
    }
  }

  public static @NotNull List<CommonIntentionAction> getIncreaseLanguageLevelFixes(
    @NotNull PsiElement element, @NotNull JavaFeature feature) {
    if (PsiUtil.isAvailable(feature, element)) return List.of();
    if (feature.isLimited()) return List.of(); //no reason for applying it because it can be outdated
    LanguageLevel applicableLevel = getApplicableLevel(element.getContainingFile(), feature);
    if (applicableLevel == LanguageLevel.JDK_X) return List.of(); // do not suggest to use experimental level
    return List.of(getFixFactory().createIncreaseLanguageLevelFix(applicableLevel),
                   getFixFactory().createUpgradeSdkFor(applicableLevel),
                   getFixFactory().createShowModulePropertiesFix(element));
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
          if (pusher instanceof JavaLanguageLevelPusher javaPusher) {
            String newMessage = javaPusher.getInconsistencyLanguageLevelMessage(message, level, file);
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
