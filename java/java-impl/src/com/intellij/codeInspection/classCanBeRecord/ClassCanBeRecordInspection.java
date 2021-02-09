// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.classCanBeRecord.ConvertToRecordFix.RecordCandidate;
import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.util.ui.CheckBox;
import com.intellij.util.ui.JBUI;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ClassCanBeRecordInspection extends BaseInspection {
  @NotNull
  public ConversionStrategy myConversionStrategy = ConversionStrategy.SHOW_AFFECTED_MEMBERS;
  public boolean suggestAccessorsRenaming;

  public ClassCanBeRecordInspection() {
  }

  public ClassCanBeRecordInspection(@NotNull ConversionStrategy conversionStrategy, boolean suggestAccessorsRenaming) {
    myConversionStrategy = conversionStrategy;
    this.suggestAccessorsRenaming = suggestAccessorsRenaming;
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return HighlightingFeature.RECORDS.isAvailable(file);
  }

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    return JavaBundle.message("class.can.be.record.display.name");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassCanBeRecordVisitor(myConversionStrategy != ConversionStrategy.DO_NOT_SUGGEST, suggestAccessorsRenaming);
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return myConversionStrategy == ConversionStrategy.SHOW_AFFECTED_MEMBERS;
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new ConvertToRecordFix(myConversionStrategy == ConversionStrategy.SHOW_AFFECTED_MEMBERS, suggestAccessorsRenaming);
  }

  @Override
  public @Nullable JComponent createOptionsPanel() {
    JPanel panel = new InspectionOptionsPanel();
    panel.add(new CheckBox(JavaBundle.message("class.can.be.record.suggest.renaming.accessors"), this,
                           "suggestAccessorsRenaming"));

    panel.add(new JLabel(JavaBundle.message("class.can.be.record.conversion.weakens.member")));
    ButtonGroup butGr = new ButtonGroup();
    for (ConversionStrategy strategy : ConversionStrategy.values()) {
      JRadioButton radioBut = new JRadioButton(strategy.getMessage(), strategy == myConversionStrategy);
      radioBut.addActionListener(e -> myConversionStrategy = strategy);
      radioBut.setBorder(JBUI.Borders.emptyLeft(20));
      butGr.add(radioBut);
      panel.add(radioBut);
    }

    return panel;
  }

  private static class ClassCanBeRecordVisitor extends BaseInspectionVisitor {
    private final boolean myRenameIfWeakenVisibility;
    private final boolean mySuggestAccessorsRenaming;

    private ClassCanBeRecordVisitor(boolean renameIfWeakenVisibility, boolean suggestAccessorsRenaming) {
      myRenameIfWeakenVisibility = renameIfWeakenVisibility;
      mySuggestAccessorsRenaming = suggestAccessorsRenaming;
    }

    @Override
    public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);
      PsiIdentifier classIdentifier = aClass.getNameIdentifier();
      if (classIdentifier == null) return;
      RecordCandidate recordCandidate = ConvertToRecordFix.getClassDefinition(aClass, mySuggestAccessorsRenaming);
      if (recordCandidate == null) return;
      if (!myRenameIfWeakenVisibility && !ConvertToRecordProcessor.findWeakenVisibilityUsages(recordCandidate).isEmpty()) return;
      if (UnusedSymbolUtil.isImplicitUsage(aClass.getProject(), aClass)) return;
      registerError(classIdentifier);
    }
  }

  public enum ConversionStrategy {
    DO_NOT_SUGGEST("class.can.be.record.conversion.strategy.do.not.convert"),
    SHOW_AFFECTED_MEMBERS("class.can.be.record.conversion.strategy.show.members"),
    SILENTLY("class.can.be.record.conversion.strategy.convert.silently");

    @Nls
    private final String messageKey;

    ConversionStrategy(@Nls String messageKey) {
      this.messageKey = messageKey;
    }

    @Nls
    String getMessage() {
      return JavaBundle.message(messageKey);
    }
  }
}
