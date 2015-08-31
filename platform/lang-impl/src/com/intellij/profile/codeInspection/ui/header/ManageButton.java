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
package com.intellij.profile.codeInspection.ui.header;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Batkovich
 */
public class ManageButton extends ComboBoxAction implements DumbAware {

  private final ManageButtonBuilder myBuilder;

  public ManageButton(final ManageButtonBuilder builder) {
    myBuilder = builder;
    getTemplatePresentation().setText("Manage");
    setSmallVariant(false);
  }

  public JComponent build() {
    return createCustomComponent(getTemplatePresentation());
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new ShareWithTeamCheckBoxAction());
    group.addSeparator();

    group.add(new CopyAction());
    group.add(new RenameAction());
    group.add(new DeleteAction());
    group.add(new EditDescriptionAction(myBuilder.hasDescription()));
    group.add(new ExportAction());
    group.addSeparator();

    group.add(new ImportAction());

    return group;
  }

  private class ShareWithTeamCheckBoxAction extends CheckboxAction implements DumbAware {
    public ShareWithTeamCheckBoxAction() {
      super("Copy to Project");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myBuilder.isProjectLevel();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myBuilder.setIsProjectLevel(state);
    }
  }

  private class CopyAction extends AnAction implements DumbAware {
    public CopyAction() {
      super("Copy");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myBuilder.copy();
    }
  }

  private class RenameAction extends AnAction implements DumbAware {
    public RenameAction() {
      super("Rename");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myBuilder.rename();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myBuilder.canRename());
    }
  }

  private class DeleteAction extends AnAction implements DumbAware {
    public DeleteAction() {
      super("Delete");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myBuilder.delete();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myBuilder.canDelete());
    }
  }

  private class EditDescriptionAction extends AnAction implements DumbAware {
    public EditDescriptionAction(boolean hasDescription) {
      super(hasDescription ? "Edit description" : "Add description");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myBuilder.editDescription();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myBuilder.canEditDescription());
    }
  }

  private class ExportAction extends AnAction implements DumbAware {
    public ExportAction() {
      super("Export...");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myBuilder.export();
    }
  }

  private class ImportAction extends AnAction implements DumbAware {
    public ImportAction() {
      super("Import...");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myBuilder.doImport();
    }
  }
}
