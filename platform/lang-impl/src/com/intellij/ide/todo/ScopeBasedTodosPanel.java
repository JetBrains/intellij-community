// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@ApiStatus.Internal
public final class ScopeBasedTodosPanel extends TodoPanel {

  private final Alarm myAlarm;
  private ScopeChooserCombo myScopes;

  public ScopeBasedTodosPanel(@NotNull TodoView todoView,
                              @NotNull TodoPanelSettings settings,
                              @NotNull Content content) {
    super(todoView, settings, false, content);

    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    myScopes.getChildComponent().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        rebuildWithAlarm(ScopeBasedTodosPanel.this.myAlarm);
        myTodoView.getState().selectedScope = myScopes.getSelectedScopeName();
      }
    });
    rebuildWithAlarm(myAlarm);
  }

  @Override
  protected JComponent createCenterComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    final JComponent component = super.createCenterComponent();
    panel.add(component, BorderLayout.CENTER);
    String preselect = myTodoView.getState().selectedScope;
    myScopes = new ScopeChooserCombo(myProject, false, true, preselect);
    Disposer.register(this, myScopes);
    
    myScopes.setCurrentSelection(false);
    myScopes.setUsageView(false);

    JPanel chooserPanel = new JPanel(new GridBagLayout());
    final JLabel scopesLabel = new JLabel(IdeBundle.message("label.scope"));
    scopesLabel.setLabelFor(myScopes);
    final GridBagConstraints gc =
      new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                             JBUI.insets(2, 8, 2, 4), 0, 0);
    chooserPanel.add(scopesLabel, gc);
    gc.insets = JBUI.insets(2);
    chooserPanel.add(myScopes, gc);

    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;
    chooserPanel.add(Box.createHorizontalBox(), gc);
    panel.add(chooserPanel, BorderLayout.NORTH);
    return panel;
  }

  @Override
  protected @NotNull TodoTreeBuilder createTreeBuilder(@NotNull JTree tree,
                                                       @NotNull Project project) {
    ScopeBasedTodosTreeBuilder builder = new ScopeBasedTodosTreeBuilder(tree, project, myScopes);
    builder.init();
    return builder;
  }
}