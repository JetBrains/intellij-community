// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * created at Sep 12, 2001
 * @author Jeka
 */
public class FailedConversionsDialog extends DialogWrapper {
  private final @Nls String[] myConflictDescriptions;
  public static final int VIEW_USAGES_EXIT_CODE = NEXT_USER_EXIT_CODE;

  public FailedConversionsDialog(@Nls String[] conflictDescriptions, Project project) {
    super(project, true);
    myConflictDescriptions = conflictDescriptions;
    setTitle(RefactoringBundle.message("problems.detected.title"));
    setOKButtonText(JavaRefactoringBundle.message("ignore.button"));
    getOKAction().putValue(Action.MNEMONIC_KEY, Integer.valueOf('I'));
    init();
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), new ViewUsagesAction(), new CancelAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    final JEditorPane messagePane = new JEditorPane(UIUtil.HTML_MIME, "");
    messagePane.setEditorKit(HTMLEditorKitBuilder.simple());
    messagePane.setEditable(false);
    messagePane.setMargin(JBUI.insets(5));
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(messagePane);
    scrollPane.setPreferredSize(JBUI.size(500, 400));

    JPanel panel = UI.PanelFactory.panel(scrollPane)
      .withLabel(RefactoringBundle.message("the.following.problems.were.found"))
      .moveLabelOnTop()
      .resizeY(true)
      .createPanel();

    HtmlBuilder builder = new HtmlBuilder();
    for (@Nls String conflictDescription : myConflictDescriptions) {
      builder.appendRaw(conflictDescription).br().br();
    }
    String text = builder.wrapWithHtmlBody().toString();
    messagePane.setText(text);
    return panel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.typeMigration.ui.FailedConversionsDialog";
  }

  private class CancelAction extends AbstractAction {
    CancelAction() {
      super(RefactoringBundle.message("cancel.button"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      doCancelAction();
    }
  }

  private class ViewUsagesAction extends AbstractAction {
    ViewUsagesAction() {
      super(RefactoringBundle.message("view.usages"));
      putValue(Action.MNEMONIC_KEY, Integer.valueOf('V'));
      putValue(DialogWrapper.DEFAULT_ACTION, Boolean.TRUE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      close(VIEW_USAGES_EXIT_CODE);
    }
  }
}
