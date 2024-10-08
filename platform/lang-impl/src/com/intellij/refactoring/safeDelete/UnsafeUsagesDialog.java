// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

@ApiStatus.Internal
public final class UnsafeUsagesDialog extends DialogWrapper {
  private JEditorPane myMessagePane;
  private final String @Nls [] myConflictDescriptions;
  public static final int VIEW_USAGES_EXIT_CODE = NEXT_USER_EXIT_CODE;

  public UnsafeUsagesDialog(String[] conflictDescriptions, Project project) {
    super(project, true);
    myConflictDescriptions = conflictDescriptions;
    setTitle(RefactoringBundle.message("usages.detected"));
    setOKButtonText(RefactoringBundle.message("delete.anyway.button"));
    init();
  }

  @Override
  protected Action @NotNull [] createActions() {
    final ViewUsagesAction viewUsagesAction = new ViewUsagesAction();

    final Action ignoreAction = getOKAction();
    ignoreAction.putValue(DialogWrapper.DEFAULT_ACTION, null);

    return new Action[]{viewUsagesAction, ignoreAction, new CancelAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    myMessagePane = new JEditorPane(UIUtil.HTML_MIME, "");
    myMessagePane.setEditable(false);
    myMessagePane.setEditorKit(HTMLEditorKitBuilder.simple());
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myMessagePane);
    scrollPane.setPreferredSize(JBUI.size(500, 400));
    panel.add(new JLabel(RefactoringBundle.message("the.following.problems.were.found")), BorderLayout.NORTH);
    panel.add(scrollPane, BorderLayout.CENTER);

    @Nls StringBuilder buf = new StringBuilder();
    for (String description : myConflictDescriptions) {
      buf.append(description);
      buf.append("<br><br>");
    }
    myMessagePane.setText(buf.toString());
    return panel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.safeDelete.UnsafeUsagesDialog";
  }

/*
  protected JComponent createSouthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(super.createSouthPanel(), BorderLayout.CENTER);
//    panel.add(new JLabel("Do you wish to ignore them and continue?"), BorderLayout.WEST);
    return panel;
  }
*/

  private final class CancelAction extends AbstractAction {
    CancelAction() {
      super(RefactoringBundle.message("cancel.button"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      doCancelAction();
    }
  }

  private final class ViewUsagesAction extends AbstractAction {
    ViewUsagesAction() {
      super(RefactoringBundle.message("view.usages"));
      putValue(DialogWrapper.DEFAULT_ACTION, Boolean.TRUE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      close(VIEW_USAGES_EXIT_CODE);
    }
  }
}
