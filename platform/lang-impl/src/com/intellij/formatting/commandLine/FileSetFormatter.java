// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.commandLine;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.service.CoreFormattingService;
import com.intellij.formatting.service.FormattingService;
import com.intellij.formatting.service.FormattingServiceUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

public final class FileSetFormatter extends FileSetProcessor {
  private static final Logger LOG = Logger.getInstance(FileSetFormatter.class);

  private final static String PROJECT_DIR_PREFIX = PlatformUtils.getPlatformPrefix() + ".format.";
  private final static String PROJECT_DIR_SUFFIX = ".tmp";

  private final static String RESULT_MESSAGE_OK = "OK";
  private final static String RESULT_MESSAGE_FAILED = "Failed";
  private final static String RESULT_MESSAGE_NOT_SUPPORTED = "Skipped, not supported.";
  private final static String RESULT_MESSAGE_REJECTED_BY_FORMATTER = "Skipped, rejected by formatter.";
  private final static String RESULT_MESSAGE_BINARY_FILE = "Skipped, binary file.";

  private final static String RESULT_MESSAGE_DRY_OK = "Formatted well";
  private final static String RESULT_MESSAGE_DRY_FAIL = "Needs reformatting";

  private final @NotNull String myProjectUID;
  private @Nullable Project myProject;
  private final MessageOutput myMessageOutput;
  private @NotNull CodeStyleSettings mySettings;
  private boolean isDryRun = false;

  public FileSetFormatter(@NotNull MessageOutput messageOutput) {
    myMessageOutput = messageOutput;
    mySettings = CodeStyleSettingsManager.getInstance().createSettings();
    myProjectUID = UUID.randomUUID().toString();
  }

  public void setDryRun(boolean isDryRun) {
    this.isDryRun = isDryRun;
  }

  public boolean isDryRun() {
    return isDryRun;
  }

  public void setCodeStyleSettings(@NotNull CodeStyleSettings settings) {
    mySettings = settings;
  }

  private void createProject() throws IOException {
    myProject = ProjectManagerEx.getInstanceEx().openProject(createProjectDir(), OpenProjectTask.newProject());
    if (myProject != null) {
      CodeStyle.setMainProjectSettings(myProject, mySettings);
    }
  }

  private @NotNull Path createProjectDir() throws IOException {
    Path projectDir = FileUtil.createTempDirectory(PROJECT_DIR_PREFIX, myProjectUID + PROJECT_DIR_SUFFIX).toPath().resolve(PathMacroUtil.DIRECTORY_STORE_NAME);
    Files.createDirectories(projectDir);
    return projectDir;
  }

  private void closeProject() {
    if (myProject != null) {
      ProjectManagerEx.getInstanceEx().closeAndDispose(myProject);
    }
  }

  @Override
  public void processFiles() throws IOException {
    createProject();
    if (myProject != null) {
      super.processFiles();
      closeProject();
    }
  }

  @Override
  public void processFiles(FileSetProcessingStatistics stats) throws IOException {
    createProject();
    if (myProject != null) {
      super.processFiles(stats);
      closeProject();
    }
  }

  @Override
  protected boolean processFile(@NotNull VirtualFile virtualFile, @NotNull FileSetProcessingStatistics stats) {
    assert myProject != null;

    stats.fileTraversed();

    String operation = isDryRun ? "Checking " : "Formatting ";
    myMessageOutput.info(operation + virtualFile.getCanonicalPath() + "...");

    if (virtualFile.getFileType().isBinary()) {
      myMessageOutput.info(RESULT_MESSAGE_BINARY_FILE + "\n");
      return false;
    }

    String resultMessage = isDryRun
                           ? processFileInternalDry(virtualFile)
                           : processFileInternal(virtualFile);

    myMessageOutput.info(resultMessage + "\n");

    if (RESULT_MESSAGE_OK.equals(resultMessage) || RESULT_MESSAGE_DRY_OK.equals(resultMessage)) {
      stats.fileProcessed(true);
      return true;
    }
    else if (RESULT_MESSAGE_DRY_FAIL.equals(resultMessage)) {
      stats.fileProcessed(false);
      return true;
    }
    else {
      return false;
    }
  }

  private String processFileInternalDry(@NotNull VirtualFile virtualFile) {

    if (virtualFile.getFileType().isBinary()) {
      return RESULT_MESSAGE_BINARY_FILE;
    }

    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) {
      LOG.warn("No document available for " + virtualFile.getPath());
      return RESULT_MESSAGE_FAILED;
    }

    String originalContent = document.getText();

    PsiFile psiCopy = PsiFileFactory.getInstance(myProject).createFileFromText(
      "a." + virtualFile.getFileType().getDefaultExtension(), virtualFile.getFileType(), originalContent, LocalTimeCounter.currentTime(),
      false
    );

    CodeStyleManager
      .getInstance(myProject)
      .reformatText(psiCopy, 0, psiCopy.getTextLength());

    String reformattedContent = psiCopy.getText();

    return Objects.equals(originalContent, reformattedContent)
           ? RESULT_MESSAGE_DRY_OK
           : RESULT_MESSAGE_DRY_FAIL;
  }

  private String processFileInternal(@NotNull VirtualFile virtualFile) {
    String resultMessage = RESULT_MESSAGE_OK;
    VfsUtil.markDirtyAndRefresh(false, false, false, virtualFile);
    if (!virtualFile.getFileType().isBinary()) {
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document != null) {
        PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        NonProjectFileWritingAccessProvider.allowWriting(Collections.singletonList(virtualFile));
        if (psiFile != null) {
          if (isFormattingSupported(psiFile)) {
            try {
              reformatFile(myProject, psiFile, document);
            }
            catch (ProcessCanceledException pce) {
              final String cause = StringUtil.notNullize(pce.getCause() != null ? pce.getCause().getMessage() : pce.getMessage());
              LOG.warn(virtualFile.getCanonicalPath() + ": " + RESULT_MESSAGE_REJECTED_BY_FORMATTER + " " + cause);
              resultMessage = RESULT_MESSAGE_REJECTED_BY_FORMATTER;
            }
            FileDocumentManager.getInstance().saveDocument(document);
          }
          else {
            resultMessage = RESULT_MESSAGE_NOT_SUPPORTED;
          }
        }
        else {
          LOG.warn("Unable to get a PSI file for " + virtualFile.getPath());
          resultMessage = RESULT_MESSAGE_FAILED;
        }
      }
      else {
        LOG.warn("No document available for " + virtualFile.getPath());
        resultMessage = RESULT_MESSAGE_FAILED;
      }
      FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
      VirtualFile[] openFiles = editorManager.getOpenFiles();
      for (VirtualFile openFile : openFiles) {
        editorManager.closeFile(openFile);
      }
    }
    else {
      resultMessage = RESULT_MESSAGE_BINARY_FILE;
    }
    return resultMessage;
  }

  private static void reformatFile(@NotNull Project project, @NotNull final PsiFile file, @NotNull Document document) {
    WriteCommandAction.runWriteCommandAction(project, () -> {
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      codeStyleManager.reformatText(file, 0, file.getTextLength());
      PsiDocumentManager.getInstance(project).commitDocument(document);
    });
  }

  private static boolean isFormattingSupported(@NotNull PsiFile file) {
    FormattingService formattingService = FormattingServiceUtil.findService(file, true, true);
    if (formattingService instanceof CoreFormattingService) {
      return LanguageFormatting.INSTANCE.forContext(file) != null;
    }
    return true;
  }
}
