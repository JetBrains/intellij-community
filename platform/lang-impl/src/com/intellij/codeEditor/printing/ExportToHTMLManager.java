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

package com.intellij.codeEditor.printing;

import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;

class ExportToHTMLManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeEditor.printing.ExportToHTMLManager");
  private static FileNotFoundException myLastException;

  private ExportToHTMLManager() {
  }

  /**
   * Should be invoked in event dispatch thread
   */
  public static void executeExport(final DataContext dataContext) throws FileNotFoundException {
    PsiDirectory psiDirectory = null;
    PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (psiElement instanceof PsiDirectory) {
      psiDirectory = (PsiDirectory)psiElement;
    }
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
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

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    boolean isSelectedTextEnabled = false;
    if (editor != null && editor.getSelectionModel().hasSelection()) {
      isSelectedTextEnabled = true;
    }
    ExportToHTMLDialog exportToHTMLDialog = new ExportToHTMLDialog(shortFileName, directoryName, isSelectedTextEnabled, project);

    ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(project);
    if (exportToHTMLSettings.OUTPUT_DIRECTORY == null) {
      final VirtualFile baseDir = project.getBaseDir();

      if (baseDir != null) {
        exportToHTMLSettings.OUTPUT_DIRECTORY = baseDir.getPresentableUrl() + File.separator + "exportToHTML";
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
    final String outputDirectoryName = exportToHTMLSettings.OUTPUT_DIRECTORY;
    if (exportToHTMLSettings.getPrintScope() != PrintSettings.PRINT_DIRECTORY) {
      if (psiFile == null || psiFile.getText() == null) {
        return;
      }
      final String dirName = constructOutputDirectory(psiFile, outputDirectoryName);
      HTMLTextPainter textPainter = new HTMLTextPainter(psiFile, project, dirName, exportToHTMLSettings.PRINT_LINE_NUMBERS);
      if (exportToHTMLSettings.getPrintScope() == PrintSettings.PRINT_SELECTED_TEXT &&
          editor != null &&
          editor.getSelectionModel().hasSelection()) {
        int firstLine = editor.getDocument().getLineNumber(editor.getSelectionModel().getSelectionStart());
        textPainter.setSegment(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd(), firstLine);
      }
      textPainter.paint(null, psiFile.getFileType());
      if (exportToHTMLSettings.OPEN_IN_BROWSER) {
        BrowserUtil.browse(textPainter.getHTMLFileName());
      }
    }
    else {
      myLastException = null;
      ExportRunnable exportRunnable = new ExportRunnable(exportToHTMLSettings, psiDirectory, outputDirectoryName, project);
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(exportRunnable, CodeEditorBundle.message("export.to.html.title"), true, project);
      if (myLastException != null) {
        throw myLastException;
      }
    }
  }

  private static boolean exportPsiFile(final PsiFile psiFile,
                                       final String outputDirectoryName,
                                       final Project project,
                                       final HashMap<PsiFile, PsiFile> filesMap) {
    final ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(project);

    if (psiFile instanceof PsiBinaryFile) {
      return true;
    }

    ApplicationManager.getApplication().runReadAction(() -> {
      if (!psiFile.isValid()) {
        return;
      }
      TreeMap<Integer, PsiReference> refMap = null;
      for (PrintOption printOption : Extensions.getExtensions(PrintOption.EP_NAME)) {
        final TreeMap<Integer, PsiReference> map = printOption.collectReferences(psiFile, filesMap);
        if (map != null) {
          refMap = new TreeMap<>();
          refMap.putAll(map);
        }
      }

      String dirName = constructOutputDirectory(psiFile, outputDirectoryName);
      HTMLTextPainter textPainter = new HTMLTextPainter(psiFile, project, dirName, exportToHTMLSettings.PRINT_LINE_NUMBERS);
      try {
        textPainter.paint(refMap, psiFile.getFileType());
      }
      catch (FileNotFoundException e) {
        myLastException = e;
      }
    });
    return myLastException == null;
  }

  private static String constructOutputDirectory(PsiFile psiFile, String outputDirectoryName) {
    return constructOutputDirectory(psiFile.getContainingDirectory(), outputDirectoryName);
  }

  private static String constructOutputDirectory(@NotNull final PsiDirectory directory, final String outputDirectoryName) {
    String qualifiedName = PsiDirectoryFactory.getInstance(directory.getProject()).getQualifiedName(directory, false);
    String dirName = outputDirectoryName;
    if(qualifiedName.length() > 0) {
      dirName += File.separator + qualifiedName.replace('.', File.separatorChar);
    }
    File dir = new File(dirName);
    dir.mkdirs();
    return dirName;
  }

  private static void addToPsiFileList(PsiDirectory psiDirectory,
                                       ArrayList<PsiFile> filesList,
                                       boolean isRecursive,
                                       final String outputDirectoryName) throws FileNotFoundException {
    if (!psiDirectory.isValid()) {
      return;
    }
    PsiFile[] files = psiDirectory.getFiles();
    Collections.addAll(filesList, files);
    generateIndexHtml(psiDirectory, isRecursive, outputDirectoryName);
    if (isRecursive) {
      PsiDirectory[] directories = psiDirectory.getSubdirectories();
      for (PsiDirectory directory : directories) {
        addToPsiFileList(directory, filesList, isRecursive, outputDirectoryName);
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void generateIndexHtml(final PsiDirectory psiDirectory, final boolean recursive, final String outputDirectoryName)
    throws FileNotFoundException {
    String indexHtmlName = constructOutputDirectory(psiDirectory, outputDirectoryName) + File.separator + "index.html";
    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(indexHtmlName), CharsetToolkit.UTF8_CHARSET);
    final String title = PsiDirectoryFactory.getInstance(psiDirectory.getProject()).getQualifiedName(psiDirectory, true);
    try {
      writer.write("<html><head><title>" + title + "</title></head><body>");
      if (recursive) {
        PsiDirectory[] directories = psiDirectory.getSubdirectories();
        for(PsiDirectory directory: directories) {
          writer.write("<a href=\"" + directory.getName() + "/index.html\"><b>" + directory.getName() + "</b></a><br />");
        }
      }
      PsiFile[] files = psiDirectory.getFiles();
      for(PsiFile file: files) {
        if (!(file instanceof PsiBinaryFile)) {
          writer.write("<a href=\"" + getHTMLFileName(file) + "\">" + file.getVirtualFile().getName() + "</a><br />");
        }
      }
      writer.write("</body></html>");
      writer.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static class ExportRunnable implements Runnable {
    private final ExportToHTMLSettings myExportToHTMLSettings;
    private final PsiDirectory myPsiDirectory;
    private final String myOutputDirectoryName;
    private final Project myProject;

    public ExportRunnable(ExportToHTMLSettings exportToHTMLSettings,
                          PsiDirectory psiDirectory,
                          String outputDirectoryName,
                          Project project) {
      myExportToHTMLSettings = exportToHTMLSettings;
      myPsiDirectory = psiDirectory;
      myOutputDirectoryName = outputDirectoryName;
      myProject = project;
    }

    @Override
    public void run() {
      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();

      final ArrayList<PsiFile> filesList = new ArrayList<>();
      final boolean isRecursive = myExportToHTMLSettings.isIncludeSubdirectories();

      ApplicationManager.getApplication().runReadAction(() -> {
        try {
          addToPsiFileList(myPsiDirectory, filesList, isRecursive, myOutputDirectoryName);
        }
        catch (FileNotFoundException e) {
          myLastException = e;
        }
      });
      if (myLastException != null) {
        return;
      }
      HashMap<PsiFile, PsiFile> filesMap = new HashMap<>();
      for (PsiFile psiFile : filesList) {
        filesMap.put(psiFile, psiFile);
      }
      for(int i = 0; i < filesList.size(); i++) {
        PsiFile psiFile = filesList.get(i);
        if(progressIndicator.isCanceled()) {
          return;
        }
        progressIndicator.setText(CodeEditorBundle.message("export.to.html.generating.file.progress", getHTMLFileName(psiFile)));
        progressIndicator.setFraction(((double)i)/filesList.size());
        if (!exportPsiFile(psiFile, myOutputDirectoryName, myProject, filesMap)) {
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

  static String getHTMLFileName(PsiFile psiFile) {
    //noinspection HardCodedStringLiteral
    return psiFile.getVirtualFile().getName() + ".html";
  }
}
