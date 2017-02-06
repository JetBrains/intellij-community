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
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
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

abstract class CodeStyleSchemesActions extends AbstractSchemeActions<CodeStyleScheme> {

  private final static String SHARED_IMPORT_SOURCE = ApplicationBundle.message("import.scheme.shared");

  protected CodeStyleSchemesActions(@NotNull AbstractSchemesPanel<CodeStyleScheme> schemesPanel) {
    super(schemesPanel);
  }

  @Override
  protected void resetScheme(@NotNull CodeStyleScheme scheme) {
    if (Messages
          .showOkCancelDialog(ApplicationBundle.message("settings.code.style.reset.to.defaults.message"),
                              ApplicationBundle.message("settings.code.style.reset.to.defaults.title"), Messages.getQuestionIcon()) ==
        Messages.OK) {
      scheme.resetToDefaults();
      getModel().fireSchemeChanged(scheme);
    }
  }

  @Override
  protected void duplicateScheme(@NotNull CodeStyleScheme scheme, @NotNull String newName) {
    if (!getModel().isProjectScheme(scheme)) {
      CodeStyleScheme newScheme = getModel().createNewScheme(newName, getCurrentScheme());
      getModel().addScheme(newScheme, true);
    }
  }

  @Override
  protected void deleteScheme(@NotNull CodeStyleScheme scheme) {
    getModel().removeScheme(scheme);
  }

  @Override
  protected void importScheme(@NotNull String importerName) {
    CodeStyleScheme currentScheme = getCurrentScheme();
    if (currentScheme != null) {
      chooseAndImport(currentScheme, importerName);
    }
  }

  @Override
  protected void copyToIDE(@NotNull CodeStyleScheme scheme) {
    String name =
      SchemeNameGenerator.getUniqueName(getProjectName(), schemeName -> getModel().containsScheme(schemeName));
    CodeStyleScheme newScheme = getModel().exportProjectScheme(name);
    getModel().setUsePerProjectSettings(false);
    getModel().selectScheme(newScheme, null);
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
            getModel().addScheme(scheme, true);
          }
        }
      }.show(getModel().getSchemes());
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
        schemeImported = importer.importScheme(getModel().getProject(), selectedFile, currentScheme, schemeCreator);
      if (schemeImported != null) {
        if (schemeCreator.isSchemeWasCreated()) {
          getModel().fireSchemeListChanged();
        }
        else {
          getModel().fireSchemeChanged(schemeImported);
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
      CodeStyleScheme newScheme = getModel().createNewScheme(targetName, getCurrentScheme());
      getModel().addScheme(newScheme, true);
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

  @Override
  public void copyToProject(@NotNull CodeStyleScheme scheme) {
    int copyToProjectConfirmation = Messages
      .showYesNoDialog(ApplicationBundle.message("settings.editor.scheme.copy.to.project.message", scheme.getName()),
                       ApplicationBundle.message("settings.editor.scheme.copy.to.project.title"), 
                       Messages.getQuestionIcon());
    if (copyToProjectConfirmation == Messages.YES) {
      getModel().copyToProject(scheme);
      getModel().setUsePerProjectSettings(true, true);
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

  @NotNull
  @Override
  protected CodeStyleSchemesModel getModel() {
    return (CodeStyleSchemesModel)super.getModel();
  }

}