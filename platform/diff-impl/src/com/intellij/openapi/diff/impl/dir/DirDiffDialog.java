// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.Action;
import javax.swing.JComponent;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class DirDiffDialog extends DialogWrapper {
  private final DirDiffTableModel myModel;
  private DirDiffPanel myDiffPanel;

  public DirDiffDialog(Project project, DirDiffTableModel model) {
    super(project);
    setModal(false);
    myModel = model;
    setTitle(DiffBundle.message("directory.diff"));
    init();
    final JBTable table = myDiffPanel.getTable();
    table.setColumnSelectionAllowed(false);
    table.getTableHeader().setReorderingAllowed(false);
    table.getTableHeader().setResizingAllowed(false);
    Disposer.register(getDisposable(), myModel);
    Disposer.register(project, getDisposable());
  }

  @Override
  protected String getDimensionServiceKey() {
    setSize(800, 600);
    return "DirDiffDialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    myDiffPanel = new DirDiffPanel(myModel, new DirDiffWindow.Dialog(this));
    Disposer.register(getDisposable(), myDiffPanel);
    return myDiffPanel.getPanel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDiffPanel.getTable();
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{};
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.diff.folder";
  }
}
