// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullDialog;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteSideEffectsAwareFix;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInsight.daemon.impl.quickfix.UnwrapSwitchLabelFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.fix.FindDfaProblemCauseFix;
import com.intellij.codeInspection.dataFlow.fix.ReplaceWithBooleanEqualsFix;
import com.intellij.codeInspection.dataFlow.fix.SurroundWithRequireNonNullFix;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.codeInspection.ui.InspectionOptionContainer;
import com.intellij.codeInspection.ui.OptionAccessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.siyeh.ig.dataflow.CreateNullBranchFix;
import com.siyeh.ig.fixes.IntroduceVariableFix;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.java.JavaBundle.message;
import static com.intellij.xml.util.XmlStringUtil.wrapInHtml;
import static javax.swing.SwingConstants.TOP;

public class DataFlowInspection extends DataFlowInspectionBase {
  private static final Logger LOG = Logger.getInstance(DataFlowInspection.class);

  @Override
  protected LocalQuickFix[] createConditionalAssignmentFixes(boolean evaluatesToTrue, PsiAssignmentExpression assignment, final boolean onTheFly) {
    IElementType op = assignment.getOperationTokenType();
    boolean toRemove = op == JavaTokenType.ANDEQ && !evaluatesToTrue || op == JavaTokenType.OREQ && evaluatesToTrue;
    if (toRemove && !onTheFly) {
      return LocalQuickFix.EMPTY_ARRAY;
    }
    return new LocalQuickFix[]{toRemove ? new RemoveAssignmentFix() : createSimplifyToAssignmentFix()};
  }

  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  @Override
  protected LocalQuickFix createReplaceWithTrivialLambdaFix(Object value) {
    return new ReplaceWithTrivialLambdaFix(value);
  }

  @Override
  protected LocalQuickFix createMutabilityViolationFix(PsiElement violation, boolean onTheFly) {
    return WrapWithMutableCollectionFix.createFix(violation, onTheFly);
  }

  @Nullable
  @Override
  protected LocalQuickFix createExplainFix(PsiExpression anchor, TrackingRunner.DfaProblemType problemType) {
    return new FindDfaProblemCauseFix(IGNORE_ASSERT_STATEMENTS, anchor, problemType);
  }

  @Nullable
  @Override
  protected LocalQuickFix createUnwrapSwitchLabelFix() {
    return new UnwrapSwitchLabelFix();
  }

  @Override
  protected LocalQuickFix createIntroduceVariableFix() {
    return new IntroduceVariableFix(true);
  }

  @Override
  protected LocalQuickFixOnPsiElement createSimplifyBooleanFix(PsiElement element, boolean value) {
    if (!(element instanceof PsiExpression)) return null;
    if (PsiTreeUtil.findChildOfType(element, PsiAssignmentExpression.class) != null) return null;

    final PsiExpression expression = (PsiExpression)element;
    while (element.getParent() instanceof PsiExpression) {
      element = element.getParent();
    }
    final SimplifyBooleanExpressionFix fix = new SimplifyBooleanExpressionFix(expression, value);
    // simplify intention already active
    if (!fix.isAvailable() || SimplifyBooleanExpressionFix.canBeSimplified((PsiExpression)element)) return null;
    return fix;
  }

  private static boolean isVolatileFieldReference(PsiExpression qualifier) {
    PsiElement target = qualifier instanceof PsiReferenceExpression ? ((PsiReferenceExpression)qualifier).resolve() : null;
    return target instanceof PsiField && ((PsiField)target).hasModifierProperty(PsiModifier.VOLATILE);
  }

  @NotNull
  @Override
  protected List<LocalQuickFix> createMethodReferenceNPEFixes(PsiMethodReferenceExpression methodRef, boolean onTheFly) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    ContainerUtil.addIfNotNull(fixes, StreamFilterNotNullFix.makeFix(methodRef));
    if (onTheFly) {
      fixes.add(new ReplaceWithTernaryOperatorFix.ReplaceMethodRefWithTernaryOperatorFix());
    }
    return fixes;
  }

  @Override
  protected LocalQuickFix createRemoveAssignmentFix(PsiAssignmentExpression assignment) {
    if (assignment == null || assignment.getRExpression() == null || !(assignment.getParent() instanceof PsiExpressionStatement)) {
      return null;
    }
    return new DeleteSideEffectsAwareFix((PsiStatement)assignment.getParent(), assignment.getRExpression(), true);
  }

  @Override
  @NotNull
  protected List<LocalQuickFix> createCastFixes(PsiTypeCastExpression castExpression,
                                                PsiType realType,
                                                boolean onTheFly,
                                                boolean alwaysFails) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    PsiExpression operand = castExpression.getOperand();
    PsiTypeElement typeElement = castExpression.getCastType();
    if (typeElement != null && operand != null) {
      if (!alwaysFails && !SideEffectChecker.mayHaveSideEffects(operand) && CodeBlockSurrounder.canSurround(castExpression)) {
        String suffix = " instanceof " + typeElement.getText();
        fixes.add(new AddAssertStatementFix(ParenthesesUtils.getText(operand, PsiPrecedenceUtil.RELATIONAL_PRECEDENCE) + suffix));
        if (onTheFly && SurroundWithIfFix.isAvailable(operand)) {
          fixes.add(new SurroundWithIfFix(operand, suffix));
        }
      }
      if (realType != null) {
        PsiType operandType = operand.getType();
        if (operandType != null) {
          PsiType type = typeElement.getType();
          PsiType[] types = {realType};
          if (realType instanceof PsiIntersectionType) {
            types = ((PsiIntersectionType)realType).getConjuncts();
          }
          for (PsiType psiType : types) {
            if (!psiType.isAssignableFrom(operandType)) {
              psiType = DfaPsiUtil.tryGenerify(operand, psiType);
              fixes.add(new ReplaceTypeInCastFix(type, psiType));
            }
          }
        }
      }
    }
    return fixes;
  }

  @Override
  @NotNull
  protected List<LocalQuickFix> createNPEFixes(@Nullable PsiExpression qualifier, PsiExpression expression, boolean onTheFly) {
    qualifier = PsiUtil.deparenthesizeExpression(qualifier);

    final List<LocalQuickFix> fixes = new SmartList<>();
    if (qualifier == null || expression == null) return fixes;

    try {
      ContainerUtil.addIfNotNull(fixes, StreamFilterNotNullFix.makeFix(qualifier));
      ContainerUtil.addIfNotNull(fixes, ReplaceComputeWithComputeIfPresentFix.makeFix(qualifier));
      if (isVolatileFieldReference(qualifier)) {
        ContainerUtil.addIfNotNull(fixes, createIntroduceVariableFix());
      }
      else if (!ExpressionUtils.isNullLiteral(qualifier) && !SideEffectChecker.mayHaveSideEffects(qualifier))  {
        String suffix = " != null";
        if (PsiUtil.getLanguageLevel(qualifier).isAtLeast(LanguageLevel.JDK_1_4) && CodeBlockSurrounder.canSurround(expression)) {
          String replacement = ParenthesesUtils.getText(qualifier, ParenthesesUtils.EQUALITY_PRECEDENCE) + suffix;
          fixes.add(new AddAssertStatementFix(replacement));
        }

        if (onTheFly && SurroundWithIfFix.isAvailable(qualifier)) {
          fixes.add(new SurroundWithIfFix(qualifier, suffix));
        }

        if (onTheFly && ReplaceWithTernaryOperatorFix.isAvailable(qualifier, expression)) {
          fixes.add(new ReplaceWithTernaryOperatorFix(qualifier));
        }
      }

      if (!ExpressionUtils.isNullLiteral(qualifier) && PsiUtil.isLanguageLevel7OrHigher(qualifier)) {
        fixes.add(new SurroundWithRequireNonNullFix(qualifier));
      }

      if (!ExpressionUtils.isNullLiteral(qualifier)) {
        ContainerUtil.addIfNotNull(fixes, createExplainFix(qualifier, new TrackingRunner.NullableDfaProblemType()));
      }

      ContainerUtil.addIfNotNull(fixes, DfaOptionalSupport.registerReplaceOptionalOfWithOfNullableFix(qualifier));

      addCreateNullBranchFix(qualifier, fixes);
    }

    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return fixes;
  }

  @Override
  protected @NotNull List<LocalQuickFix> createUnboxingNullableFixes(@NotNull PsiExpression qualifier, PsiExpression expression, boolean onTheFly) {
    List<LocalQuickFix> result = new SmartList<>();
    if (TypeConversionUtil.isBooleanType(qualifier.getType())) {
      result.add(new ReplaceWithBooleanEqualsFix(qualifier));
    }
    addCreateNullBranchFix(qualifier, result);
    return result;
  }

  private static void addCreateNullBranchFix(@NotNull PsiExpression qualifier, @NotNull List<LocalQuickFix> fixes) {
    if (!HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(qualifier)) return;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(qualifier.getParent());
    if (parent instanceof PsiSwitchBlock && PsiUtil.skipParenthesizedExprDown(((PsiSwitchBlock)parent).getExpression()) == qualifier) {
      fixes.add(new CreateNullBranchFix((PsiSwitchBlock)parent));
    }
  }

  @Override
  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    return new NullableStuffInspection.NavigateToNullLiteralArguments(parameter);
  }

  private final class OptionsPanel extends JPanel implements InspectionOptionContainer {
    private static final int BUTTON_OFFSET = 20;
    private final JButton myConfigureAnnotations;
    private final Map<String, @Nls String> myCheckboxes = new HashMap<>();
    private final OptionAccessor.Default myAccessor = new OptionAccessor.Default(DataFlowInspection.this);

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.weighty = 0;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      JCheckBox suggestNullables = createCheckBoxWithHTML(
        message("inspection.data.flow.nullable.quickfix.option"),
        "SUGGEST_NULLABLE_ANNOTATIONS");

      JCheckBox treatUnknownMembersAsNullable = createCheckBoxWithHTML(
        message("inspection.data.flow.treat.non.annotated.members.and.parameters.as.nullable"),
        "TREAT_UNKNOWN_MEMBERS_AS_NULLABLE");

      JCheckBox reportNullArguments = createCheckBoxWithHTML(
        message("inspection.data.flow.report.not.null.required.parameter.with.null.literal.argument.usages"),
        "REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER");

      JCheckBox reportNullableMethodsReturningNotNull = createCheckBoxWithHTML(
        message("inspection.data.flow.report.nullable.methods.that.always.return.a.non.null.value"),
        "REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL");

      JCheckBox dontReportTrueAsserts = createCheckBoxWithHTML(
        message("inspection.data.flow.true.asserts.option"),
        "DONT_REPORT_TRUE_ASSERT_STATEMENTS");

      JCheckBox ignoreAssertions = createCheckBoxWithHTML(
        message("inspection.data.flow.ignore.assert.statements"),
        "IGNORE_ASSERT_STATEMENTS");

      JCheckBox reportConstantReferences = createCheckBoxWithHTML(
        message("inspection.data.flow.warn.when.reading.a.value.guaranteed.to.be.constant"),
        "REPORT_CONSTANT_REFERENCE_VALUES");

      JCheckBox reportUnsoundWarnings = createCheckBoxWithHTML(
        message("inspection.data.flow.report.problems.that.happen.only.on.some.code.paths"),
        "REPORT_UNSOUND_WARNINGS");

      gc.insets = JBInsets.emptyInsets();
      gc.gridy = 0;
      add(suggestNullables, gc);

      myConfigureAnnotations = NullableNotNullDialog.createConfigureAnnotationsButton(this);
      gc.gridy++;
      gc.fill = GridBagConstraints.NONE;
      gc.insets.left = BUTTON_OFFSET;
      gc.insets.bottom = 15;
      add(myConfigureAnnotations, gc);

      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weighty = 1;
      gc.insets.left = 0;
      gc.gridy++;
      add(treatUnknownMembersAsNullable, gc);

      gc.gridy++;
      add(reportNullArguments, gc);

      gc.gridy++;
      add(reportNullableMethodsReturningNotNull, gc);

      gc.gridy++;
      add(dontReportTrueAsserts, gc);

      gc.gridy++;
      add(ignoreAssertions, gc);

      gc.gridy++;
      add(reportConstantReferences, gc);

      gc.gridy++;
      add(reportUnsoundWarnings, gc);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension preferred = super.getPreferredSize();
      if (!isPreferredSizeSet()) {
        // minimize preferred width to align HTML text within ScrollPane
        Dimension size = myConfigureAnnotations.getPreferredSize();
        preferred.width = size.width + BUTTON_OFFSET;
      }
      return preferred;
    }

    @Override
    public @NotNull HtmlChunk getLabelForCheckbox(@NotNull String property) {
      String label = myCheckboxes.get(property);
      if (label == null) {
        throw new IllegalArgumentException("Invalid property name: " + property);
      }
      return HtmlChunk.raw(label);
    }

    private JCheckBox createCheckBoxWithHTML(@Nls String text, String accessorId) {
      myCheckboxes.put(accessorId, text);
      JCheckBox box = new JCheckBox(wrapInHtml(text));
      box.setVerticalTextPosition(TOP);
      box.setSelected(myAccessor.getOption(accessorId));
      box.getModel().addItemListener(event -> myAccessor.setOption(accessorId, box.isSelected()));
      return box;
    }
  }
}