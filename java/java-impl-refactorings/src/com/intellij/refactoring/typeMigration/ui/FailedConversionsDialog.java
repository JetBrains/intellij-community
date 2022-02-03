/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;

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
    setTitle(JavaRefactoringBundle.message("usages.detected.title"));
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
    JPanel panel = new JPanel(new BorderLayout());
    final JEditorPane messagePane = new JEditorPane(UIUtil.HTML_MIME, "");
    messagePane.setEditorKit(HTMLEditorKitBuilder.simple());
    messagePane.setEditable(false);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(messagePane);
    scrollPane.setPreferredSize(JBUI.size(500, 400));
    panel.add(new JLabel(RefactoringBundle.message("the.following.problems.were.found")), BorderLayout.NORTH);
    panel.add(scrollPane, BorderLayout.CENTER);

    ArrayList<HtmlChunk> chunks = new ArrayList<>(myConflictDescriptions.length * 2);
    for (@Nls String conflictDescription : myConflictDescriptions) {
      chunks.add(HtmlChunk.raw(conflictDescription));
      chunks.add(HtmlChunk.br());
    }
    String text = HtmlChunk.body().children(chunks.toArray(HtmlChunk[]::new)).toString();
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
