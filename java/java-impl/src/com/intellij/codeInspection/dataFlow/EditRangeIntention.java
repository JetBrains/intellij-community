// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

public class EditRangeIntention extends BaseIntentionAction implements LowPriorityAction {
  private static final String JETBRAINS_RANGE = "org.jetbrains.annotations.Range";
  
  private static final String ourPrompt = "<html>Please specify the value range<p>" +
                                          "Leave 'from' or 'to' empty if it's unrestricted</html>";

  @NotNull
  @Override
  public String getFamilyName() {
    return "Edit range";
  }

  @Nullable
  private static PsiModifierListOwner getTarget(Editor editor, PsiFile file) {
    final PsiModifierListOwner owner =  AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset(), true);
    LongRangeSet rangeFromType = rangeFromType(owner);
    if (rangeFromType == null || !ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) return null;
    PsiElement original = owner.getOriginalElement();
    return original instanceof PsiModifierListOwner ? (PsiModifierListOwner)original : owner;
  }

  @Contract("null -> null")
  private static LongRangeSet rangeFromType(PsiModifierListOwner owner) {
    PsiType type;
    if (owner instanceof PsiMethod) {
      type = ((PsiMethod)owner).getReturnType();
    } else if (owner instanceof PsiField || owner instanceof PsiParameter) {
      type = ((PsiVariable)owner).getType();
    } else {
      type = null;
    }
    return LongRangeSet.fromType(type);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiModifierListOwner owner = getTarget(editor, file);
    if (owner != null) {
      boolean hasRange = !LongRangeSet.fromPsiElement(owner).equals(LongRangeSet.all());
      String name = ((PsiNamedElement)owner).getName();
      setText(hasRange ? "Edit range of '" + name + "'" : "Add range to '" + name + "'");
      return true;
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiModifierListOwner owner = getTarget(editor, file);
    assert owner != null;
    LongRangeSet existingRange = LongRangeSet.fromPsiElement(owner);
    LongRangeSet fromType = rangeFromType(owner);
    assert fromType != null;

    String min = existingRange.min() > fromType.min() ? String.valueOf(existingRange.min()) : "";
    String max = existingRange.max() < fromType.max() ? String.valueOf(existingRange.max()) : "";
    JBTextField minText = new JBTextField(min);
    JBTextField maxText = new JBTextField(max);
    DialogBuilder builder = createDialog(project, minText, maxText);
    DocumentAdapter validator = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        Couple<String> errors = getErrorMessages(minText.getText(), maxText.getText(), fromType);
        if (errors.getFirst() != null || errors.getSecond() != null) {
          builder.setOkActionEnabled(false);
          builder.setErrorText(errors.getFirst(), minText);
          if (errors.getSecond() != null) {
            builder.setErrorText(errors.getSecond(), maxText);
          }
        }
        else {
          builder.setOkActionEnabled(true);
          builder.setErrorText(null);
        }
      }
    };
    minText.getDocument().addDocumentListener(validator);
    maxText.getDocument().addDocumentListener(validator);
    if (builder.showAndGet()) {
      updateRange(owner, fromType, minText.getText(), maxText.getText());
    }
  }

  private static DialogBuilder createDialog(@NotNull Project project,
                                            JBTextField minText,
                                            JBTextField maxText) {
    JPanel panel = new JPanel(new GridBagLayout());
    
    GridBag c = new GridBag().setDefaultAnchor(GridBagConstraints.WEST).setDefaultFill(GridBagConstraints.HORIZONTAL)
      .setDefaultInsets(JBUI.insets(2)).setDefaultWeightX(0, 1.0).setDefaultWeightX(1, 3.0).setDefaultWeightY(1.0);
    panel.add(Messages.configureMessagePaneUi(new JTextPane(), ourPrompt), c.nextLine().next().coverLine());
    
    JLabel fromLabel = new JLabel("From (inclusive):");
    fromLabel.setDisplayedMnemonic('f');
    fromLabel.setLabelFor(minText);
    panel.add(fromLabel, c.nextLine().next());
    panel.add(minText, c.next());
    
    JLabel toLabel = new JLabel("To (inclusive):");
    toLabel.setDisplayedMnemonic('t');
    toLabel.setLabelFor(maxText);
    panel.add(toLabel, c.nextLine().next());
    panel.add(maxText, c.next());

    DialogBuilder builder = new DialogBuilder(project).setNorthPanel(panel).title("Edit Range");
    builder.setPreferredFocusComponent(minText);
    builder.setHelpId("define_range_dialog");
    return builder;
  }

  private static void updateRange(PsiModifierListOwner owner, LongRangeSet fromType, String min, String max) {
    Project project = owner.getProject();
    ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(project);
    manager.deannotate(owner, JETBRAINS_RANGE);
    min = min.trim();
    max = max.trim();
    Long minValue = parseValue(min, fromType, true);
    Long maxValue = parseValue(max, fromType, false);
    if (minValue == null || maxValue == null || minValue == fromType.min() && maxValue == fromType.max()) {
      return;
    }
    if (minValue == Long.MIN_VALUE) {
      min = CommonClassNames.JAVA_LANG_LONG + ".MIN_VALUE";
    }
    if (minValue == Integer.MIN_VALUE) {
      min = CommonClassNames.JAVA_LANG_INTEGER + ".MIN_VALUE";
    }
    if (maxValue == Long.MAX_VALUE) {
      max = CommonClassNames.JAVA_LANG_LONG + ".MAX_VALUE";
    }
    if (maxValue == Integer.MAX_VALUE) {
      max = CommonClassNames.JAVA_LANG_INTEGER + ".MAX_VALUE";
    }
    if (min.isEmpty()) {
      min = minValue.toString();
    }
    if (max.isEmpty()) {
      max = maxValue.toString();
    }
    PsiAnnotation mockAnno = JavaPsiFacade.getElementFactory(project).createAnnotationFromText(
      "@"+JETBRAINS_RANGE+"(from="+min+", to="+max+")", null);
    try {
      manager.annotateExternally(owner, JETBRAINS_RANGE, owner.getContainingFile(),
                                 mockAnno.getParameterList().getAttributes());
    }
    catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {}
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  @NotNull
  private static Couple<String> getErrorMessages(String minText, String maxText, LongRangeSet fromType) {
    Long minValue = parseValue(minText, fromType, true);
    Long maxValue = parseValue(maxText, fromType, false);
    String minError = null;
    String maxError = null;
    if (minValue == null) {
      minError = "Invalid value";
    } else if (minValue < fromType.min()) {
      minError = "Should not be less than " + fromType.min();
    }
    if (maxValue == null) {
      maxError = "Invalid value";
    } else if (maxValue > fromType.max()) {
      maxError = "Should not be bigger than " + fromType.max();
    } else if (minValue != null && maxValue < minValue) {
      maxError = "Should not be less than 'from'";
    }
    return Couple.of(minError, maxError);
  }

  @Nullable
  private static Long parseValue(String text, LongRangeSet fromType, boolean isMin) {
    text = text.trim();
    Long value;
    if (text.isEmpty()) {
      return isMin ? fromType.min() : fromType.max();
    }
    value = PsiLiteralUtil.parseLong(text);
    if (value != null) return value;
    Integer intValue = PsiLiteralUtil.parseInteger(text);
    return intValue != null ? Long.valueOf(intValue.longValue()) : value;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
