// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.inlineSuperClass;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.inline.InlineOptionsDialog;
import com.intellij.refactoring.ui.DocCommentPanel;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class InlineSuperClassRefactoringDialog extends InlineOptionsDialog {
  private final PsiClass mySuperClass;
  private final PsiClass myCurrentInheritor;
  private final DocCommentPanel myDocPanel;

  protected InlineSuperClassRefactoringDialog(@NotNull Project project, PsiClass superClass, PsiClass currentInheritor) {
    super(project, false, superClass);
    mySuperClass = superClass;
    myCurrentInheritor = currentInheritor;
    myInvokedOnReference = currentInheritor != null;
    myDocPanel = new DocCommentPanel(JavaRefactoringBundle.message("inline.super.doc.panel.title"));
    myDocPanel.setPolicy(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
    init();
    setTitle(JavaRefactoringBundle.message("inline.super.class"));
  }

  @Override
  protected void doAction() {
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    if(myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_SUPER_CLASS_THIS = isInlineThisOnly();
    }
    invokeRefactoring(new InlineSuperClassRefactoringProcessor(getProject(), isInlineThisOnly() ? myCurrentInheritor : null, mySuperClass, myDocPanel.getPolicy()));
  }

  @Override
  protected JComponent createNorthPanel() {
    return null;
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "Inline_Super_Class";
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gc =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                             JBInsets.emptyInsets(), 0, 0);
    panel.add(super.createCenterPanel(), gc);
    panel.add(myDocPanel, gc);
    if (mySuperClass.getDocComment() == null) {
      boolean hasJavadoc =
        InlineSuperClassRefactoringProcessor.getClassMembersToPush(mySuperClass).stream().anyMatch(memberInfo -> {
          PsiMember member = memberInfo.getMember();
          return member instanceof PsiDocCommentOwner && ((PsiDocCommentOwner)member).getDocComment() != null;
        });
      myDocPanel.setVisible(hasJavadoc);
    }
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;
    panel.add(Box.createVerticalGlue(), gc);
    return panel;
  }

  @Override
  protected String getNameLabelText() {
    return JavaRefactoringBundle.message("inline.super.class.label", mySuperClass.getQualifiedName());
  }

  @Override
  protected String getBorderTitle() {
    return JavaRefactoringBundle.message("inline.action.name");
  }

  @Override
  protected String getInlineAllText() {
    return JavaRefactoringBundle.message("all.references.and.remove.super.class");
  }

  @Override
  protected String getInlineThisText() {
    return JavaRefactoringBundle.message("this.reference.only.and.keep.super.class");
  }

  @Override
  protected boolean isInlineThis() {
    return JavaRefactoringSettings.getInstance().INLINE_SUPER_CLASS_THIS;
  }
}