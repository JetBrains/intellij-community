// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle;

import com.intellij.CommonBundle;
import com.intellij.application.options.SchemesToImportPopup;
import com.intellij.application.options.schemes.AbstractSchemeActions;
import com.intellij.application.options.schemes.AbstractSchemesPanel;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class CodeStyleSchemesActions extends AbstractSchemeActions<CodeStyleScheme> {
  CodeStyleSchemesActions(@NotNull AbstractSchemesPanel<CodeStyleScheme, ?> schemesPanel) {
    super(schemesPanel);
  }

  @Override
  protected void resetScheme(@NotNull CodeStyleScheme scheme) {
    if (Messages
          .showOkCancelDialog(ApplicationBundle.message("settings.code.style.reset.to.defaults.message"),
                              ApplicationBundle.message("settings.code.style.reset.to.defaults.title"),
                              LangBundle.message("button.restore"), CommonBundle.getCancelButtonText(), Messages.getQuestionIcon()) ==
        Messages.OK) {
      getModel().restoreDefaults(scheme);
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
  protected void importScheme(@NotNull String importerName) {
    CodeStyleScheme currentScheme = getCurrentScheme();
    if (currentScheme != null) {
      chooseAndImport(currentScheme, importerName);
    }
  }

  @Override
  protected void copyToIDE(@NotNull CodeStyleScheme scheme) {
    getSchemesPanel().editNewSchemeName(
      getProjectName(),
      false,
      newName -> {
        CodeStyleScheme newScheme = getModel().exportProjectScheme(newName);
        getModel().selectScheme(newScheme, null);
      }
    );
  }

  @NotNull
  private String getProjectName() {
    Project project = ProjectUtil.guessCurrentProject(getSchemesPanel());
    return project.getName();
  }

  private void chooseAndImport(@NotNull CodeStyleScheme currentScheme, @NotNull String importerName) {
    if (importerName.equals(getSharedImportSource())) {
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
          getSchemesPanel().showStatus(
            ApplicationBundle.message("message.code.style.scheme.import.success", importerName, scheme.getName(),
                                      additionalImportInfo),
            MessageType.INFO);
        }
      }
      catch (SchemeImportException e) {
        if (e.isWarning()) {
          getSchemesPanel().showStatus(e.getMessage(), MessageType.WARNING);
          return;
        }
        final String message = ApplicationBundle.message("message.code.style.scheme.import.failure", importerName, e.getMessage());
        getSchemesPanel().showStatus(message, MessageType.ERROR);
      }
    }
  }

  @Nullable
  private CodeStyleScheme importExternalCodeStyle(@NotNull SchemeImporter<CodeStyleScheme> importer, @NotNull CodeStyleScheme currentScheme)
    throws SchemeImportException {
    final VirtualFile selectedFile = SchemeImportUtil
      .selectImportSource(importer.getSourceExtensions(), getSchemesPanel(), CodeStyleSchemesUIConfiguration.Util.getRecentImportFile(), null);
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
          getModel().updateScheme(schemeImported);
        }
        return schemeImported;
      }
    }
    return null;
  }

  private class SchemeCreator implements SchemeFactory<CodeStyleScheme> {
    private boolean mySchemeWasCreated;

    @NotNull
    @Override
    public CodeStyleScheme createNewScheme(@Nullable String targetName) {
      mySchemeWasCreated = true;
      if (targetName == null) targetName = ApplicationBundle.message("code.style.scheme.import.unnamed");
      CodeStyleScheme newScheme = getModel().createNewScheme(targetName, getCurrentScheme());
      getModel().addScheme(newScheme, true);
      return newScheme;
    }

    boolean isSchemeWasCreated() {
      return mySchemeWasCreated;
    }
  }

  @NotNull
  @Override
  protected Class<CodeStyleScheme> getSchemeType() {
    return CodeStyleScheme.class;
  }

  @Override
  public void copyToProject(@NotNull CodeStyleScheme scheme) {
    int copyToProjectConfirmation = MessageDialogBuilder.yesNo(ApplicationBundle.message("settings.editor.scheme.copy.to.project.title"),
                                                               ApplicationBundle.message("settings.editor.scheme.copy.to.project.message",
                                                                                         scheme.getName()))
      .show();
    if (copyToProjectConfirmation == Messages.YES) {
      getModel().copyToProject(scheme);
    }
  }

  @NotNull
  @Override
  protected CodeStyleSchemesModel getModel() {
    return (CodeStyleSchemesModel)super.getModel();
  }

  private static String getSharedImportSource() {
    return ApplicationBundle.message("import.scheme.shared");
  }
}