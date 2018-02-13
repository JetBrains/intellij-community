/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/**
 * <p>
 * A standard set of scheme actions: copy, reset, rename, etc. used in {@link SimpleSchemesPanel}. More actions can be added via
 * {@link #addAdditionalActions(List)} method. Available actions depend on {@link SchemesModel}. If schemes model supports both IDE and
 * project schemes, {@link #copyToIDE(Scheme)} and {@link #copyToProject(Scheme)} must be overridden to do the actual job, default
 * implementation for the methods does nothing.
 * <p>
 * Import and export actions are available only if there are importer/exporter implementations for the actual scheme type.
 *
 * @param <T> The actual scheme type.
 * @see SimpleSchemesPanel
 * @see SchemesModel
 * @see SchemeImporter
 * @see SchemeExporter
 */
public abstract class AbstractSchemeActions<T extends Scheme> {
  
  private final Collection<String> mySchemeImportersNames;
  private final Collection<String> mySchemeExporterNames;
  protected final AbstractSchemesPanel<T, ?> mySchemesPanel;

  protected AbstractSchemeActions(@NotNull AbstractSchemesPanel<T, ?> schemesPanel) {
    mySchemesPanel = schemesPanel;
    mySchemeImportersNames = getSchemeImportersNames();
    mySchemeExporterNames = getSchemeExporterNames();
  }
  
    
  protected Collection<String> getSchemeImportersNames() {
    List<String> importersNames = new ArrayList<>();
    for (SchemeImporterEP<T> importerEP : SchemeImporterEP.getExtensions(getSchemeType())) {
      importersNames.add(importerEP.name);
    }
    return importersNames;
  }
  
  private Collection<String> getSchemeExporterNames() {
    List<String> exporterNames = new ArrayList<>();
    for (SchemeExporterEP<T> exporterEP : SchemeExporterEP.getExtensions(getSchemeType())) {
      exporterNames.add(exporterEP.name);
    }
    return exporterNames;
  }

  public final Collection<AnAction> getActions() {
    List<AnAction> actions = new ArrayList<>();
    if (mySchemesPanel.supportsProjectSchemes()) {
      actions.add(new CopyToProjectAction());
      actions.add(new CopyToIDEAction());
      actions.add(new Separator());
    }
    actions.add(new CopyAction());
    actions.add(new RenameAction());
    addAdditionalActions(actions);
    actions.add(new ResetAction());
    actions.add(new DeleteAction());
    if (!mySchemeExporterNames.isEmpty()) {
      actions.add(createImportExportAction(ApplicationBundle.message("settings.editor.scheme.export"),
                                           mySchemeExporterNames,
                                           ExportAction::new));
    }
    actions.add(new Separator());
    if (!mySchemeImportersNames.isEmpty()) {
      actions.add(createImportExportAction(ApplicationBundle.message("settings.editor.scheme.import", mySchemesPanel.getSchemeTypeName()),
                                           mySchemeImportersNames,
                                           ImportAction::new));
    }
    return actions;
  }
  
  protected void addAdditionalActions(@NotNull List<AnAction> defaultActions) {}

  private class CopyToProjectAction extends DumbAwareAction {

    public CopyToProjectAction() {
      super(ApplicationBundle.message("settings.editor.scheme.copy.to.project"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null && !getModel().isProjectScheme(currentScheme)) {
        copyToProject(currentScheme);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      T currentScheme = getCurrentScheme();
      p.setEnabledAndVisible(currentScheme != null && !getModel().isProjectScheme(currentScheme));
    }
  }


  private class CopyToIDEAction extends DumbAwareAction {

    public CopyToIDEAction() {
      super(ApplicationBundle.message("settings.editor.scheme.copy.to.ide"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null && getModel().isProjectScheme(currentScheme)) {
        copyToIDE(currentScheme);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      T currentScheme = getCurrentScheme();
      p.setEnabledAndVisible(currentScheme != null && getModel().isProjectScheme(currentScheme));
    }
  }
  
  private class ResetAction extends DumbAwareAction {
    
    public ResetAction() {
      super(ApplicationBundle.message("settings.editor.scheme.reset"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.cancelEdit();
        resetScheme(currentScheme);
      }                                                                             
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      T scheme = getCurrentScheme();
      if(scheme != null && mySchemesPanel.getModel().canResetScheme(scheme)) {
        p.setVisible(true);
        p.setEnabled(mySchemesPanel.getModel().differsFromDefault(scheme));
      }
      else {
        p.setEnabledAndVisible(false);
      }
    }
  }
  
  
  private class CopyAction extends DumbAwareAction {
    public CopyAction() {
      super(ApplicationBundle.message("settings.editor.scheme.copy"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.editNewSchemeName(
          SchemeManager.getDisplayName(currentScheme),
          mySchemesPanel.supportsProjectSchemes() && getModel().isProjectScheme(currentScheme),
          newName ->  duplicateScheme(currentScheme, newName));
      }
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      T scheme = getCurrentScheme();
      p.setEnabledAndVisible(scheme != null && mySchemesPanel.getModel().canDuplicateScheme(scheme));
    }
  }
  
  
  private class RenameAction extends DumbAwareAction {
    public RenameAction() {
      super("Rename...");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      mySchemesPanel.editCurrentSchemeName(
        (currentScheme, newName) -> renameScheme(currentScheme, newName));
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      T scheme = getCurrentScheme();
      p.setEnabledAndVisible(scheme != null && mySchemesPanel.getModel().canRenameScheme(scheme));
    }
  }
  
  private class DeleteAction extends DumbAwareAction {
    public DeleteAction() {
      super(ApplicationBundle.message("settings.editor.scheme.delete"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.cancelEdit();
        deleteScheme(currentScheme);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      T scheme = getCurrentScheme();
      boolean isEnabled = scheme != null && mySchemesPanel.getModel().canDeleteScheme(scheme);
      if (mySchemesPanel.hideDeleteActionIfUnavailable()) {
        p.setEnabledAndVisible(isEnabled);
      }  else {
        p.setEnabled(isEnabled);
      }
    }
  }

  private static AnAction createImportExportAction(@NotNull String groupName,
                                                   @NotNull Collection<String> actionNames,
                                                   @NotNull BiFunction<String, String, AnAction> createActionByName) {
    if (actionNames.size() == 1) {
      return createActionByName.apply(ContainerUtil.getFirstItem(actionNames), groupName + "...");
    } else {
      return new ImportExportActionGroup(groupName, actionNames) {
        @NotNull
        @Override
        protected AnAction createAction(@NotNull String actionName) {
          return createActionByName.apply(actionName, actionName);
        }
      };
    }
  }

  private abstract static class ImportExportActionGroup extends ActionGroup {
    private final Collection<String> myActionNames;

    public ImportExportActionGroup(@NotNull String groupName, @NotNull Collection<String> actionNames) {
      super(groupName, true);
      myActionNames = actionNames;
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      List<AnAction> namedActions = new ArrayList<>();
      for (String actionName : myActionNames) {
        namedActions.add(createAction(actionName));
      }
      return namedActions.toArray(AnAction.EMPTY_ARRAY);
    }

    @NotNull
    protected abstract AnAction createAction(@NotNull String actionName);
  }
  
  private class ImportAction extends DumbAwareAction {

    private final String myImporterName;

    public ImportAction(@NotNull String importerName, @NotNull String importerText) {
      super(importerText);
      myImporterName = importerName;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      mySchemesPanel.cancelEdit();
      importScheme(myImporterName);
    }
  }
  
  private class ExportAction extends DumbAwareAction {
    private final String myExporterName;

    public ExportAction(@NotNull String exporterName, @NotNull String exporterText) {
      super(exporterText);
      myExporterName = exporterName;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.cancelEdit();
        exportScheme(currentScheme, myExporterName);
      }
    }
  }

  /**
   * Import a scheme using the given importer name.
   *
   * @param importerName The importer name.
   * @see SchemeImporter
   * @see SchemeImporterEP
   */
  protected void importScheme(@NotNull String importerName) {
  }

  /**
   * Reset scheme's settings to their default values (presets).
   *
   * @param scheme The scheme to reset.
   */
  protected abstract void resetScheme(@NotNull T scheme);

  /**
   * Creates a copy of the scheme with a different name.
   *
   * @param scheme  The scheme to copy.
   * @param newName New name.
   */
  protected abstract void duplicateScheme(@NotNull T scheme, @NotNull String newName);

  /**
   * Delete the scheme.
   *
   * @param scheme The scheme to delete.
   */
  protected void deleteScheme(@NotNull T scheme) {
    if (Messages.showOkCancelDialog(
      "Do you want to delete \"" + scheme.getName() + "\" " + StringUtil.toLowerCase(mySchemesPanel.getSchemeTypeName()) + "?",
      "Delete " + mySchemesPanel.getSchemeTypeName(),
      Messages.getQuestionIcon()) == Messages.OK) {
      mySchemesPanel.getModel().removeScheme(scheme);
    }
  }

  /**
   * Export the scheme using the given exporter name.
   *
   * @param scheme The scheme to export.
   * @param exporterName The exporter name.
   * @see SchemeExporter
   * @see SchemeExporterEP
   */
  protected void exportScheme(@NotNull T scheme, @NotNull String exporterName) {
    SchemeExporter<T> exporter = SchemeExporterEP.getExporter(exporterName, getSchemeType());
    if (exporter != null) {
      Object config = null;
      if (exporter instanceof ConfigurableSchemeExporter) {
        //noinspection unchecked
        config = ((ConfigurableSchemeExporter)exporter).getConfiguration(mySchemesPanel, scheme);
        if (config == null) return;
      }
      String ext = exporter.getExtension();
      FileSaverDialog saver =
        FileChooserFactory.getInstance()
          .createSaveFileDialog(new FileSaverDescriptor(
            ApplicationBundle.message("scheme.exporter.ui.file.chooser.title"),
            ApplicationBundle.message("scheme.exporter.ui.file.chooser.message"),
            ext), getSchemesPanel());
      VirtualFileWrapper target = saver.save(null, SchemeManager.getDisplayName(scheme) + "." + ext);
      if (target != null) {
        VirtualFile targetFile = target.getVirtualFile(true);
        String message;
        MessageType messageType;
        if (targetFile != null) {
          try {
            Object finalConfig = config;
            WriteAction.run(() -> {
              OutputStream outputStream = targetFile.getOutputStream(this);
              try {
                if (exporter instanceof ConfigurableSchemeExporter) {
                  //noinspection unchecked
                  ((ConfigurableSchemeExporter)exporter).exportScheme(scheme, outputStream, finalConfig);
                }
                exporter.exportScheme(scheme, outputStream);
              }
              finally {
                outputStream.close();
              }
            });
            message = ApplicationBundle
              .message("scheme.exporter.ui.scheme.exported.message",
                       scheme.getName(),
                       getSchemesPanel().getSchemeTypeName(),
                       targetFile.getPresentableUrl());
            messageType = MessageType.INFO;
          }
          catch (Exception e) {
            message = ApplicationBundle.message("scheme.exporter.ui.export.failed", e.getMessage());
            messageType = MessageType.ERROR;
          }
        }
        else {
          message = ApplicationBundle.message("scheme.exporter.ui.cannot.write.message");
          messageType = MessageType.ERROR;
        }
        getSchemesPanel().showStatus(message, messageType);
      }
    }
  }

  /**
   * Make necessary configurable updates when another scheme has been selected.
   *
   * @param scheme The new current scheme.
   */
  protected abstract void onSchemeChanged(@Nullable T scheme);

  /**
   * Change scheme's name to the new one. Called when a user stops editing by pressing Enter.
   *
   * @param scheme The scheme to rename.
   * @param newName New scheme name.
   */
  protected abstract void renameScheme(@NotNull T scheme, @NotNull String newName);

  /**
   * Copy the scheme to project. The implementation may decide what name to use.
   *
   * @param scheme The scheme to copy.
   */
  protected void copyToProject(@NotNull T scheme) {
  }

  /**
   * Copy the scheme to IDE (application). The implementation may decide what name to use.
   *
   * @param scheme The scheme to copy.
   */
  protected void copyToIDE(@NotNull T scheme) {
  }

  @NotNull
  protected SchemesModel<T> getModel() {
    return mySchemesPanel.getModel();
  }
  
  @Nullable
  protected final T getCurrentScheme() {
    return mySchemesPanel.getSelectedScheme();
  }

  /**
   * @return The actual scheme type.
   */
  protected abstract Class<T> getSchemeType();

  public final AbstractSchemesPanel<T, ?> getSchemesPanel() {
    return mySchemesPanel;
  }
}

