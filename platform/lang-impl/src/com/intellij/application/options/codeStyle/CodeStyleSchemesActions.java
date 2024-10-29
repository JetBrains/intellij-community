// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  private @NotNull String getProjectName() {
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

  private @Nullable CodeStyleScheme importExternalCodeStyle(@NotNull SchemeImporter<CodeStyleScheme> importer, @NotNull CodeStyleScheme currentScheme)
    throws SchemeImportException {
    final VirtualFile selectedFile = SchemeImportUtil
      .selectImportSource(importer.getSourceExtensions(), getSchemesPanel(), CodeStyleSchemesUIConfiguration.Util.getRecentImportFile(), null);
    if (selectedFile != null) {
      CodeStyleSchemesUIConfiguration.Util.setRecentImportFile(selectedFile);
      final SchemeCreator schemeCreator = new SchemeCreator();
      CodeStyleScheme importedScheme = null;
      try {
        importedScheme = importer.importScheme(getModel().getProject(), selectedFile, currentScheme, schemeCreator);
        if (importedScheme != null) {
          if (schemeCreator.isSchemeWasCreated()) {
            getModel().fireSchemeListChanged();
          }
          else {
            getModel().updateScheme(importedScheme);
          }
          return importedScheme;
        }
      }
      finally {
        if (importedScheme == null && schemeCreator.isSchemeWasCreated()) {
          getModel().removeScheme(schemeCreator.getCreatedScheme());
          getModel().selectScheme(currentScheme, null);
        }
      }
    }
    return null;
  }

  @Override
  protected @NotNull Class<CodeStyleScheme> getSchemeType() {
    return CodeStyleScheme.class;
  }

  @Override
  protected @NotNull CodeStyleSchemesModel getModel() {
    return (CodeStyleSchemesModel)super.getModel();
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

  private final class SchemeCreator implements SchemeFactory<CodeStyleScheme> {
    private CodeStyleScheme myCreatedScheme = null;

    @Override
    public @NotNull CodeStyleScheme createNewScheme(@Nullable String targetName) {
      if (targetName == null) targetName = ApplicationBundle.message("code.style.scheme.import.unnamed");
      myCreatedScheme = getModel().createNewScheme(targetName, getCurrentScheme());
      getModel().addScheme(myCreatedScheme, true);
      return myCreatedScheme;
    }

    boolean isSchemeWasCreated() {
      return myCreatedScheme != null;
    }

    CodeStyleScheme getCreatedScheme() {
      return myCreatedScheme;
    }
  }

  private static String getSharedImportSource() {
    return ApplicationBundle.message("import.scheme.shared");
  }
}