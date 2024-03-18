// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.schemes;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsActions;
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
  protected final @NotNull AbstractSchemesPanel<T, ?> mySchemesPanel;

  protected AbstractSchemeActions(@NotNull AbstractSchemesPanel<T, ?> schemesPanel) {
    mySchemesPanel = schemesPanel;
  }
  

  protected @NotNull Collection<String> getSchemeImportersNames() {
    List<String> importersNames = new ArrayList<>();
    for (SchemeImporterEP<T> importerEP : SchemeImporterEP.getExtensions(getSchemeType())) {
      importersNames.add(importerEP.getLocalizedName());
    }
    return importersNames;
  }

  private @NotNull Collection<String> getSchemeExporterNames() {
    List<String> exporterNames = new ArrayList<>();
    for (SchemeExporterEP<T> exporterEP : SchemeExporterEP.getExtensions(getSchemeType())) {
      exporterNames.add(exporterEP.getLocalizedName());
    }
    return exporterNames;
  }

  public @NotNull Collection<AnAction> getActions() {
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

    actions.addAll(getExportImportActions(true));

    return actions;
  }

  protected List<AnAction> getExportImportActions(boolean withSeparator) {
    List<AnAction> actions = new ArrayList<>();

    @NotNull Collection<String> schemeImportersNames = getSchemeImportersNames();
    @NotNull Collection<String> schemeExporterNames = getSchemeExporterNames();
    if (!schemeExporterNames.isEmpty()) {
      actions.add(createImportExportAction(ApplicationBundle.message("settings.editor.scheme.export"),
                                           schemeExporterNames,
                                           ExportAction::new));
    }

    if (withSeparator) {
      actions.add(new Separator());
    }

    if (!schemeImportersNames.isEmpty()) {
      actions.add(createImportExportAction(ApplicationBundle.message("settings.editor.scheme.import", mySchemesPanel.getSchemeTypeName()),
                                           schemeImportersNames,
                                           ImportAction::new));
    }

    return actions;
  }
  
  protected void addAdditionalActions(@NotNull List<? super AnAction> defaultActions) {}

  private final class CopyToProjectAction extends DumbAwareAction {

    CopyToProjectAction() {
      super(ApplicationBundle.messagePointer("settings.editor.scheme.copy.to.project"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null && !getModel().isProjectScheme(currentScheme)) {
        copyToProject(currentScheme);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation p = e.getPresentation();
      T currentScheme = getCurrentScheme();
      p.setEnabledAndVisible(currentScheme != null && !getModel().isProjectScheme(currentScheme));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
}


  private final class CopyToIDEAction extends DumbAwareAction {

    CopyToIDEAction() {
      super(ApplicationBundle.messagePointer("settings.editor.scheme.copy.to.ide"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null && getModel().isProjectScheme(currentScheme)) {
        copyToIDE(currentScheme);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation p = e.getPresentation();
      T currentScheme = getCurrentScheme();
      p.setEnabledAndVisible(currentScheme != null && getModel().isProjectScheme(currentScheme));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
}
  
  private final class ResetAction extends DumbAwareAction {
    
    ResetAction() {
      super(ApplicationBundle.messagePointer("settings.editor.scheme.reset"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.cancelEdit();
        resetScheme(currentScheme);
      }                                                                             
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
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

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }
  
  
  private final class CopyAction extends DumbAwareAction {
    CopyAction() {
      super(ApplicationBundle.messagePointer("settings.editor.scheme.copy"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.editNewSchemeName(
          currentScheme.getDisplayName(),
          mySchemesPanel.supportsProjectSchemes() && getModel().isProjectScheme(currentScheme),
          newName ->  duplicateScheme(currentScheme, newName));
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation p = e.getPresentation();
      T scheme = getCurrentScheme();
      p.setEnabledAndVisible(scheme != null && mySchemesPanel.getModel().canDuplicateScheme(scheme));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }
  
  
  private final class RenameAction extends DumbAwareAction {
    RenameAction() {
      super(ActionsBundle.messagePointer("action.RenameAction.text"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      mySchemesPanel.editCurrentSchemeName(
        (currentScheme, newName) -> renameScheme(currentScheme, newName));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation p = e.getPresentation();
      T scheme = getCurrentScheme();
      p.setEnabledAndVisible(scheme != null && mySchemesPanel.getModel().canRenameScheme(scheme));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }
  
  private final class DeleteAction extends DumbAwareAction {
    DeleteAction() {
      super(ApplicationBundle.messagePointer("settings.editor.scheme.delete"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.cancelEdit();
        deleteScheme(currentScheme);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation p = e.getPresentation();
      T scheme = getCurrentScheme();
      boolean isEnabled = scheme != null && mySchemesPanel.getModel().canDeleteScheme(scheme);
      if (mySchemesPanel.hideDeleteActionIfUnavailable()) {
        p.setEnabledAndVisible(isEnabled);
      }
      else {
        p.setEnabled(isEnabled);
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  protected static @NotNull AnAction createImportExportAction(@NotNull @NlsActions.ActionText String groupName,
                                                            @NotNull Collection<@NlsActions.ActionText String> actionNames,
                                                            @NotNull BiFunction<? super String, ? super @NlsActions.ActionText String, ? extends AnAction> createActionByName) {
    if (actionNames.size() == 1) {
      return createActionByName.apply(ContainerUtil.getFirstItem(actionNames), groupName + "...");
    }
    return new ImportExportActionGroup(groupName, actionNames) {
      @Override
      protected @NotNull AnAction createAction(@NotNull String actionName) {
        return createActionByName.apply(actionName, actionName);
      }
    };
  }

  private abstract static class ImportExportActionGroup extends ActionGroup {
    private final Collection<@NlsActions.ActionText String> myActionNames;

    ImportExportActionGroup(@NotNull @NlsActions.ActionText String groupName, @NotNull Collection<@NlsActions.ActionText String> actionNames) {
      super(groupName, true);
      myActionNames = actionNames;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      List<AnAction> namedActions = new ArrayList<>();
      for (String actionName : myActionNames) {
        namedActions.add(createAction(actionName));
      }
      return namedActions.toArray(AnAction.EMPTY_ARRAY);
    }

    protected abstract @NotNull AnAction createAction(@NotNull String actionName);
  }
  
  private final class ImportAction extends DumbAwareAction {

    private final String myImporterName;

    ImportAction(@NotNull String importerName, @NotNull @NlsActions.ActionText String importerText) {
      super(importerText);
      myImporterName = importerName;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      mySchemesPanel.cancelEdit();
      importScheme(myImporterName);
    }
  }
  
  private final class ExportAction extends DumbAwareAction {
    private final String myExporterName;

    ExportAction(@NotNull String exporterName, @NotNull @NlsActions.ActionText String exporterText) {
      super(exporterText);
      myExporterName = exporterName;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      T currentScheme = getCurrentScheme();
      if (currentScheme != null) {
        mySchemesPanel.cancelEdit();
        Project project = e.getProject();
        exportScheme(project, currentScheme, myExporterName);
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
  private void deleteScheme(@NotNull T scheme) {
    if (Messages.showOkCancelDialog(
      IdeBundle.message("message.do.you.want.to.delete.0.1", scheme.getName(), StringUtil.toLowerCase(mySchemesPanel.getSchemeTypeName())),
      IdeBundle.message("dialog.title.delete.0", mySchemesPanel.getSchemeTypeName()), IdeBundle.message("button.delete"), CommonBundle.getCancelButtonText(),
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
  protected void exportScheme(@Nullable Project project, @NotNull T scheme, @NotNull String exporterName) {
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
      VirtualFileWrapper target = saver.save(exporter.getDefaultDir(project), exporter.getDefaultFileName(scheme.getDisplayName()) + "." + ext);
      if (target != null) {
        VirtualFile targetFile = target.getVirtualFile(true);
        String message;
        MessageType messageType;
        if (targetFile != null) {
          try {
            Object finalConfig = config;
            WriteAction.run(() -> {
              try (OutputStream outputStream = targetFile.getOutputStream(this)) {
                if (exporter instanceof ConfigurableSchemeExporter) {
                  //noinspection unchecked
                  ((ConfigurableSchemeExporter)exporter).exportScheme(scheme, outputStream, finalConfig);
                }
                exporter.exportScheme(project, scheme, outputStream);
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

  protected @NotNull SchemesModel<T> getModel() {
    return mySchemesPanel.getModel();
  }
  
  protected final @Nullable T getCurrentScheme() {
    return mySchemesPanel.getSelectedScheme();
  }

  /**
   * @return The actual scheme type.
   */
  protected abstract @NotNull Class<T> getSchemeType();

  public final @NotNull AbstractSchemesPanel<T, ?> getSchemesPanel() {
    return mySchemesPanel;
  }
}

