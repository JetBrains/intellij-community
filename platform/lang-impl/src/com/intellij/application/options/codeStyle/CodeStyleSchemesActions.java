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
package com.intellij.application.options.codeStyle;

import com.intellij.application.options.SchemesToImportPopup;
import com.intellij.application.options.schemes.AbstractSchemeActions;
import com.intellij.application.options.schemes.AbstractSchemesPanel;
import com.intellij.application.options.schemes.SchemeNameGenerator;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.util.List;

abstract class CodeStyleSchemesActions extends AbstractSchemeActions<CodeStyleScheme> {

  private final static String SHARED_IMPORT_SOURCE = ApplicationBundle.message("import.scheme.shared");

  protected CodeStyleSchemesActions(@NotNull AbstractSchemesPanel<CodeStyleScheme> schemesPanel) {
    super(schemesPanel);
  }


  @Override
  protected void addAdditionalActions(@NotNull List<AnAction> defaultActions) {
    defaultActions.add(0, new CopyToProjectAction());
    defaultActions.add(1, new CopyToIDEAction());
    defaultActions.add(2, new Separator());
  }

  private class CopyToProjectAction extends DumbAwareAction {

    public CopyToProjectAction() {
      super(ApplicationBundle.message("settings.editor.scheme.copy.to.project"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      CodeStyleScheme currentScheme = getCurrentScheme();
      if (currentScheme != null && !getSchemesModel().isProjectScheme(currentScheme)) {
        copyToProject(currentScheme);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      CodeStyleScheme currentScheme = getCurrentScheme();
      p.setEnabledAndVisible(currentScheme != null && !getSchemesModel().isProjectScheme(currentScheme));
    }
  }
  
  
  private class CopyToIDEAction extends DumbAwareAction {

    public CopyToIDEAction() {
      super(ApplicationBundle.message("settings.editor.scheme.copy.to.ide"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
       CodeStyleScheme currentScheme = getCurrentScheme();
      if (currentScheme != null && getSchemesModel().isProjectScheme(currentScheme)) {
        exportProjectScheme();
      }
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      CodeStyleScheme currentScheme = getCurrentScheme();
      p.setEnabledAndVisible(currentScheme != null && getSchemesModel().isProjectScheme(currentScheme));
    }
  }

  @Override
  protected void resetScheme(@NotNull CodeStyleScheme scheme) {
    if (Messages
          .showOkCancelDialog(ApplicationBundle.message("settings.code.style.reset.to.defaults.message"),
                              ApplicationBundle.message("settings.code.style.reset.to.defaults.title"), Messages.getQuestionIcon()) ==
        Messages.OK) {
      scheme.resetToDefaults();
      getSchemesModel().fireSchemeChanged(scheme);
    }
  }

  @Override
  protected void duplicateScheme(@NotNull CodeStyleScheme scheme, @NotNull String newName) {
    if (!getSchemesModel().isProjectScheme(scheme)) {
      CodeStyleScheme newScheme = getSchemesModel().createNewScheme(newName, getCurrentScheme());
      getSchemesModel().addScheme(newScheme, true);
    }
  }

  @Override
  protected void deleteScheme(@NotNull CodeStyleScheme scheme) {
    getSchemesModel().removeScheme(scheme);
  }

  @Override
  protected void importScheme(@NotNull String importerName) {
    CodeStyleScheme currentScheme = getCurrentScheme();
    if (currentScheme != null) {
      chooseAndImport(currentScheme, importerName);
    }
  }

  private void exportProjectScheme() {
    String name =
      SchemeNameGenerator.getUniqueName(getProjectName(), schemeName -> getSchemesModel().nameExists(schemeName));
    CodeStyleScheme newScheme = getSchemesModel().exportProjectScheme(name);
    getSchemesModel().setUsePerProjectSettings(false);
    getSchemesModel().selectScheme(newScheme, null);
    getSchemesPanel().startEdit();
  }

  @NotNull
  private String getProjectName() {
    Project project = ProjectUtil.guessCurrentProject(getSchemesPanel());
    return project.getName();
  }
  
  private void chooseAndImport(@NotNull CodeStyleScheme currentScheme, @NotNull String importerName) {
    if (importerName.equals(SHARED_IMPORT_SOURCE)) {
      new SchemesToImportPopup<CodeStyleScheme>(getSchemesPanel()) {
        @Override
        protected void onSchemeSelected(CodeStyleScheme scheme) {
          if (scheme != null) {
            getSchemesModel().addScheme(scheme, true);
          }
        }
      }.show(getSchemesModel().getSchemes());
    }
    else {
      final SchemeImporter<CodeStyleScheme> importer = SchemeImporterEP.getImporter(importerName, CodeStyleScheme.class);
      if (importer == null) return;
      try {
        final CodeStyleScheme scheme = importExternalCodeStyle(importer, currentScheme);
        if (scheme != null) {
          final String additionalImportInfo = StringUtil.notNullize(importer.getAdditionalImportInfo(scheme));
          SchemeImportUtil
            .showStatus(getSchemesPanel(),
                        ApplicationBundle.message("message.code.style.scheme.import.success", importerName, scheme.getName(),
                                                  additionalImportInfo),
                        MessageType.INFO);
        }
      }
      catch (SchemeImportException e) {
        if (e.isWarning()) {
          SchemeImportUtil.showStatus(getSchemesPanel(), e.getMessage(), MessageType.WARNING);
          return;
        }
        final String message = ApplicationBundle.message("message.code.style.scheme.import.failure", importerName, e.getMessage());
        SchemeImportUtil.showStatus(getSchemesPanel(), message, MessageType.ERROR);
      }
    }
  }

  @Nullable
  private CodeStyleScheme importExternalCodeStyle(final SchemeImporter<CodeStyleScheme> importer, @NotNull CodeStyleScheme currentScheme)
    throws SchemeImportException {
    final VirtualFile selectedFile = SchemeImportUtil
      .selectImportSource(importer.getSourceExtensions(), getSchemesPanel(), CodeStyleSchemesUIConfiguration.Util.getRecentImportFile());
    if (selectedFile != null) {
      CodeStyleSchemesUIConfiguration.Util.setRecentImportFile(selectedFile);
      final SchemeCreator schemeCreator = new SchemeCreator();
      final CodeStyleScheme
        schemeImported = importer.importScheme(getSchemesModel().getProject(), selectedFile, currentScheme, schemeCreator);
      if (schemeImported != null) {
        if (schemeCreator.isSchemeWasCreated()) {
          getSchemesModel().fireSchemeListChanged();
        }
        else {
          getSchemesModel().fireSchemeChanged(schemeImported);
        }
        return schemeImported;
      }
    }
    return null;
  }

  private class SchemeCreator implements SchemeFactory<CodeStyleScheme> {
    private boolean mySchemeWasCreated;

    @Override
    public CodeStyleScheme createNewScheme(@Nullable String targetName) {
      mySchemeWasCreated = true;
      if (targetName == null) targetName = ApplicationBundle.message("code.style.scheme.import.unnamed");
      CodeStyleScheme newScheme = getSchemesModel().createNewScheme(targetName, getCurrentScheme());
      getSchemesModel().addScheme(newScheme, true);
      return newScheme;
    }

    public boolean isSchemeWasCreated() {
      return mySchemeWasCreated;
    }
  }

  @Override
  protected Class<CodeStyleScheme> getSchemeType() {
    return CodeStyleScheme.class;
  }

  public void copyToProject(CodeStyleScheme scheme) {
    int copyToProjectConfirmation = Messages
      .showYesNoDialog(ApplicationBundle.message("settings.editor.scheme.copy.to.project.message", scheme.getName()),
                       ApplicationBundle.message("settings.editor.scheme.copy.to.project.title"), 
                       Messages.getQuestionIcon());
    if (copyToProjectConfirmation == Messages.YES) {
      getSchemesModel().copyToProject(scheme);
      getSchemesModel().setUsePerProjectSettings(true, true);
    }
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void exportScheme(@NotNull CodeStyleScheme scheme, @NotNull String exporterName) {
    SchemeExporter<CodeStyleScheme> exporter = SchemeExporterEP.getExporter(exporterName, CodeStyleScheme.class);
    if (exporter != null) {
      String ext = exporter.getExtension();
      FileSaverDialog saver =
        FileChooserFactory.getInstance()
          .createSaveFileDialog(new FileSaverDescriptor(
            ApplicationBundle.message("scheme.exporter.ui.file.chooser.title"),
            ApplicationBundle.message("scheme.exporter.ui.file.chooser.message"),
            ext), getSchemesPanel());
      VirtualFileWrapper target = saver.save(null, scheme.getName() + "." + ext);
      if (target != null) {
        VirtualFile targetFile = target.getVirtualFile(true);
        String message;
        MessageType messageType;
        if (targetFile != null) {
          try {
            WriteAction.run(() -> {
              OutputStream outputStream = targetFile.getOutputStream(this);
              try {
                exporter.exportScheme(scheme, outputStream);
              }
              finally {
                outputStream.close();
              }
            });
            message = ApplicationBundle
              .message("scheme.exporter.ui.code.style.exported.message", scheme.getName(), targetFile.getPresentableUrl());
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
        SchemeImportUtil.showStatus(getSchemesPanel(), message, messageType);
      }
    }
  }
  
  protected abstract CodeStyleSchemesModel getSchemesModel();
}