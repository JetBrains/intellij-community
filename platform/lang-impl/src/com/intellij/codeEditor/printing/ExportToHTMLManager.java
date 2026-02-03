// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeEditor.printing;

import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

final class ExportToHTMLManager {
  private static final Logger LOG = Logger.getInstance(ExportToHTMLManager.class);
  private IOException myLastException;

  /**
   * Should be invoked in event dispatch thread
   */
  void executeExport(@NotNull DataContext dataContext) throws NoSuchFileException {
    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    PsiDirectory psiDirectory = null;
    if (psiFile != null) {
      psiDirectory = psiFile.getContainingDirectory();
    }
    else {
      PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      if (psiElement instanceof PsiDirectory) {
        psiDirectory = (PsiDirectory)psiElement;
      }
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    Project project = psiDirectory != null ? psiDirectory.getProject() : editor != null ? editor.getProject()
                                                                                        : CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    String shortFileName = null;
    String directoryName = null;
    if (psiFile != null || psiDirectory != null) {
      if (psiFile != null) {
        shortFileName = psiFile.getVirtualFile().getName();
        if (psiDirectory == null) {
          psiDirectory = psiFile.getContainingDirectory();
        }
      }
      if (psiDirectory != null) {
        directoryName = psiDirectory.getVirtualFile().getPresentableUrl();
      }
    }

    final boolean isSelectedTextEnabled = editor != null && editor.getSelectionModel().hasSelection();

    ExportToHTMLDialog exportToHTMLDialog = new ExportToHTMLDialog(shortFileName, directoryName, isSelectedTextEnabled, project);

    ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(project);
    if (exportToHTMLSettings.OUTPUT_DIRECTORY == null) {
      String baseDir = Objects.requireNonNull(project).getBasePath();
      if (baseDir != null) {
        exportToHTMLSettings.OUTPUT_DIRECTORY = baseDir + File.separator + "exportToHTML";
      }
      else {
        exportToHTMLSettings.OUTPUT_DIRECTORY = "";
      }
    }
    exportToHTMLDialog.reset();
    if (!exportToHTMLDialog.showAndGet()) {
      return;
    }

    try {
      exportToHTMLDialog.apply();
    }
    catch (ConfigurationException e) {
      Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    Path outDir = Paths.get(exportToHTMLSettings.OUTPUT_DIRECTORY);
    try {
      if (exportToHTMLSettings.getPrintScope() == PrintSettings.PRINT_DIRECTORY) {
        myLastException = null;
        Runnable exportRunnable = new ExportRunnable(exportToHTMLSettings, psiDirectory, outDir, project);
        ProgressManager.getInstance().runProcessWithProgressSynchronously(exportRunnable, EditorBundle.message("export.to.html.title"), true, project);
        IOException exception = myLastException;
        if (exception != null) {
          if (exception instanceof NoSuchFileException) {
            throw (NoSuchFileException)exception;
          }
          else {
            LOG.error(exception);
          }
        }
      }
      else {
        if (psiFile == null || psiFile.getText() == null) {
          return;
        }

        boolean hasSelection = editor != null && editor.getSelectionModel().hasSelection();
        int selectionStart;
        int firstLine;
        int selectionEnd;
        if (hasSelection) {
          selectionStart = editor.getSelectionModel().getSelectionStart();
          firstLine = editor.getDocument().getLineNumber(selectionStart);
          selectionEnd = editor.getSelectionModel().getSelectionEnd();
        }
        else {
          selectionStart = firstLine = selectionEnd = 0;
        }
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          ApplicationManager.getApplication().runReadAction(() -> {
            if (!psiFile.isValid()) {
              return;
            }
            try {
              HTMLTextPainter textPainter = new HTMLTextPainter(psiFile, project, new HtmlStyleManager(false),
                                                                exportToHTMLSettings.PRINT_LINE_NUMBERS, true);
              if (exportToHTMLSettings.getPrintScope() == PrintSettings.PRINT_SELECTED_TEXT && hasSelection) {
                textPainter.setSegment(selectionStart, selectionEnd, firstLine);
              }

              Path htmlFile = doPaint(constructOutputDirectory(psiFile.getContainingDirectory(), outDir), textPainter, null);
              if (exportToHTMLSettings.OPEN_IN_BROWSER) {
                BrowserUtil.browse(htmlFile);
              }
            }
            catch (IOException e) {
              LOG.error(e);
            }
          });
        }, EditorBundle.message("export.to.html.title"), true, project);
      }
    }
    finally {
      VfsUtil.markDirtyAndRefresh(true, true, false, new File(exportToHTMLSettings.OUTPUT_DIRECTORY));
    }
  }

  private static @NotNull Path doPaint(@NotNull Path outDir,
                                       @NotNull HTMLTextPainter textPainter,
                                       @Nullable Int2ObjectMap<? extends PsiReference> refMap) throws IOException {
    Path htmlFile = outDir.resolve(getHTMLFileName(textPainter.getPsiFile()));
    try (BufferedWriter writer = Files.newBufferedWriter(htmlFile)) {
      textPainter.paint(refMap, writer, true);
    }
    return htmlFile;
  }

  private boolean exportPsiFile(@NotNull PsiFile psiFile,
                                @NotNull Path outDir,
                                Project project,
                                Map<PsiFile, PsiFile> filesMap) {
    if (psiFile instanceof PsiBinaryFile) {
      return true;
    }

    ApplicationManager.getApplication().runReadAction(() -> {
      if (!psiFile.isValid()) {
        return;
      }

      Int2ObjectMap<PsiReference> refMap = null;
      for (PrintOption printOption : PrintOption.EP_NAME.getExtensionList()) {
        Map<Integer, PsiReference> map = printOption.collectReferences(psiFile, filesMap);
        if (map != null) {
          refMap = new Int2ObjectRBTreeMap<>(map);
        }
      }

      try {
        HTMLTextPainter painter = new HTMLTextPainter(psiFile, project, new HtmlStyleManager(false),
                                                      ExportToHTMLSettings.getInstance(project).PRINT_LINE_NUMBERS, true);
        doPaint(constructOutputDirectory(psiFile.getContainingDirectory(), outDir), painter, refMap);
      }
      catch (NoSuchFileException e) {
        myLastException = e;
      }
      catch (IOException e) {
        LOG.error(e);
      }
    });
    return myLastException == null;
  }

  private static @NotNull Path constructOutputDirectory(@NotNull PsiDirectory directory, @NotNull Path outDir) throws IOException {
    String qualifiedName = PsiDirectoryFactory.getInstance(directory.getProject()).getQualifiedName(directory, false);
    Path dir = outDir;
    if (!qualifiedName.isEmpty()) {
      dir = dir.resolve(qualifiedName.replace('.', File.separatorChar));
    }
    Files.createDirectories(dir);
    return dir;
  }

  private static void addToPsiFileList(@NotNull PsiDirectory psiDirectory,
                                       @NotNull List<? super PsiFile> fileList,
                                       boolean isRecursive,
                                       @NotNull Path outputDirectoryName) throws IOException {
    if (!psiDirectory.isValid()) {
      return;
    }

    Collections.addAll(fileList, psiDirectory.getFiles());
    generateIndexHtml(psiDirectory, isRecursive, outputDirectoryName);
    if (isRecursive) {
      for (PsiDirectory directory : psiDirectory.getSubdirectories()) {
        addToPsiFileList(directory, fileList, true, outputDirectoryName);
      }
    }
  }

  private static void generateIndexHtml(PsiDirectory psiDirectory, boolean recursive, @NotNull Path outDir)
    throws IOException {
    Path indexHtmlName = constructOutputDirectory(psiDirectory, outDir).resolve("index.html");
    String title = PsiDirectoryFactory.getInstance(psiDirectory.getProject()).getQualifiedName(psiDirectory, true);
    try (BufferedWriter writer = Files.newBufferedWriter(indexHtmlName)) {
      writer.write("<html><head><title>" + title + "</title></head><body>");
      if (recursive) {
        PsiDirectory[] directories = psiDirectory.getSubdirectories();
        for (PsiDirectory directory : directories) {
          writer.write("<a href=\"" + directory.getName() + "/index.html\"><b>" + directory.getName() + "</b></a><br />");
        }
      }
      PsiFile[] files = psiDirectory.getFiles();
      for (PsiFile file : files) {
        if (!(file instanceof PsiBinaryFile)) {
          writer.write("<a href=\"" + getHTMLFileName(file) + "\">" + file.getVirtualFile().getName() + "</a><br />");
        }
      }
      writer.write("</body></html>");
    }
  }

  private final class ExportRunnable implements Runnable {
    private final @NotNull ExportToHTMLSettings myExportToHTMLSettings;
    private final PsiDirectory myPsiDirectory;
    private final @NotNull Path outDir;
    private final @NotNull Project myProject;

    ExportRunnable(@NotNull ExportToHTMLSettings exportToHTMLSettings,
                   PsiDirectory psiDirectory,
                   @NotNull Path outputDirectoryName,
                   @NotNull Project project) {
      myExportToHTMLSettings = exportToHTMLSettings;
      myPsiDirectory = psiDirectory;
      outDir = outputDirectoryName;
      myProject = project;
    }

    @Override
    public void run() {
      List<PsiFile> filesList = new ArrayList<>();
      boolean isRecursive = myExportToHTMLSettings.isIncludeSubdirectories();

      ApplicationManager.getApplication().runReadAction(() -> {
        try {
          addToPsiFileList(myPsiDirectory, filesList, isRecursive, outDir);
        }
        catch (IOException e) {
          if (myLastException == null) {
            myLastException = e;
          }
          else {
            LOG.error(e);
          }
        }
      });

      if (myLastException != null) {
        return;
      }

      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      Map<PsiFile, PsiFile> filesMap = new HashMap<>();
      for (PsiFile psiFile : filesList) {
        filesMap.put(psiFile, psiFile);
      }
      for (int i = 0; i < filesList.size(); i++) {
        PsiFile psiFile = filesList.get(i);
        if (progressIndicator.isCanceled()) {
          return;
        }
        progressIndicator.setText(EditorBundle.message("export.to.html.generating.file.progress", getHTMLFileName(psiFile)));
        progressIndicator.setFraction((double)i / filesList.size());
        if (!exportPsiFile(psiFile, outDir, myProject, filesMap)) {
          return;
        }
      }
      if (myExportToHTMLSettings.OPEN_IN_BROWSER) {
        String dirToShow = myExportToHTMLSettings.OUTPUT_DIRECTORY;
        if (!dirToShow.endsWith(File.separator)) {
          dirToShow += File.separatorChar;
        }
        dirToShow += PsiDirectoryFactory.getInstance(myProject).getQualifiedName(myPsiDirectory, false).replace('.', File.separatorChar);
        BrowserUtil.browse(dirToShow);
      }
    }
  }

  static @NotNull String getHTMLFileName(@NotNull PsiFileSystemItem psiFile) {
    return psiFile.getVirtualFile().getNameSequence() + ".html";
  }
}
