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
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.print.*;
import java.util.List;

class PrintManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeEditor.printing.PrintManager");

  public static void executePrint(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    PsiDirectory[] psiDirectory = new PsiDirectory[1];
    PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (psiElement instanceof PsiDirectory) {
      psiDirectory[0] = (PsiDirectory)psiElement;
    }

    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    String[] shortFileName = new String[1];
    String[] directoryName = new String[1];
    if (psiFile != null || psiDirectory[0] != null) {
      if (psiFile != null) {
        shortFileName[0] = psiFile.getName();
        if (psiDirectory[0] == null) {
          psiDirectory[0] = psiFile.getContainingDirectory();
        }
      }
      if (psiDirectory[0] != null) {
        directoryName[0] = psiDirectory[0].getVirtualFile().getPresentableUrl();
      }
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    String text = null;
    if (editor != null) {
      if (editor.getSelectionModel().hasSelection()) {
        text = CodeEditorBundle.message("print.selected.text.radio");
      }
      else {
        text = psiFile == null ? "Console text" : null;
      }
    }

    PrintDialog printDialog = new PrintDialog(shortFileName[0], directoryName[0], text, project);
    printDialog.reset();
    if (!printDialog.showAndGet()) {
      return;
    }
    printDialog.apply();

    final PageFormat pageFormat = createPageFormat();
    final BasePainter painter;

    PrintSettings printSettings = PrintSettings.getInstance();
    if (printSettings.getPrintScope() != PrintSettings.PRINT_DIRECTORY) {
      if (psiFile == null && editor == null) return;
      TextPainter textPainter =
        psiFile != null ? initTextPainter(psiFile, editor) : initTextPainter((DocumentEx)editor.getDocument(), project);
      if (textPainter == null) return;

      if (printSettings.getPrintScope() == PrintSettings.PRINT_SELECTED_TEXT &&
          editor != null &&
          editor.getSelectionModel().hasSelection()) {
        textPainter.setSegment(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
      }
      painter = textPainter;
    }
    else {
      List<Pair<PsiFile, Editor>> filesList = ContainerUtil.newArrayList();
      boolean isRecursive = printSettings.isIncludeSubdirectories();
      addToPsiFileList(psiDirectory[0], filesList, isRecursive);
      painter = new MultiFilePainter(filesList);
    }

    final PrinterJob printerJob = PrinterJob.getPrinterJob();
    try {
      printerJob.setPrintable(painter, pageFormat);
      if (!printerJob.printDialog()) {
        return;
      }
    }
    catch (Exception e) {
      LOG.warn(e);
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    ProgressManager.getInstance()
      .run(new Task.Backgroundable(project, CodeEditorBundle.message("print.progress"), true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            painter.setProgress(indicator);
            printerJob.print();
          }
          catch (ProcessCanceledException e) {
            LOG.info("Cancelled");
            printerJob.cancel();
          }
          catch (PrinterException e) {
            LOG.warn(e);
            String message = ObjectUtils.notNull(e.getMessage(), e.getClass().getName());
            Notifications.Bus.notify(new Notification("Print", CommonBundle.getErrorTitle(), message, NotificationType.ERROR));
          }
          catch (Exception e) {
            LOG.error(e);
          }
          finally {
            painter.dispose();
          }
        }
      });
  }

  private static void addToPsiFileList(PsiDirectory psiDirectory, List<Pair<PsiFile, Editor>> filesList, boolean isRecursive) {
    PsiFile[] files = psiDirectory.getFiles();
    for (PsiFile file : files) {
      filesList.add(Pair.create(file, PsiUtilBase.findEditor(file)));
    }
    if (isRecursive) {
      for (PsiDirectory directory : psiDirectory.getSubdirectories()) {
        if (!Project.DIRECTORY_STORE_FOLDER.equals(directory.getName())) {
          addToPsiFileList(directory, filesList, true);
        }
      }
    }
  }

  private static PageFormat createPageFormat() {
    PrintSettings printSettings = PrintSettings.getInstance();
    PageFormat pageFormat = new PageFormat();
    Paper paper = new Paper();
    String paperSize = printSettings.PAPER_SIZE;
    double paperWidth = PageSizes.getWidth(paperSize) * 72;
    double paperHeight = PageSizes.getHeight(paperSize) * 72;
    double leftMargin = printSettings.LEFT_MARGIN * 72;
    double rightMargin = printSettings.RIGHT_MARGIN * 72;
    double topMargin = printSettings.TOP_MARGIN * 72;
    double bottomMargin = printSettings.BOTTOM_MARGIN * 72;

    paper.setSize(paperWidth, paperHeight);
    if (printSettings.PORTRAIT_LAYOUT) {
      pageFormat.setOrientation(PageFormat.PORTRAIT);
      paperWidth -= leftMargin + rightMargin;
      paperHeight -= topMargin + bottomMargin;
      paper.setImageableArea(leftMargin, topMargin, paperWidth, paperHeight);
    }
    else {
      pageFormat.setOrientation(PageFormat.LANDSCAPE);
      paperWidth -= topMargin + bottomMargin;
      paperHeight -= leftMargin + rightMargin;
      paper.setImageableArea(rightMargin, topMargin, paperWidth, paperHeight);
    }
    pageFormat.setPaper(paper);
    return pageFormat;
  }

  static TextPainter initTextPainter(final PsiFile psiFile, final Editor editor) {
    return ApplicationManager.getApplication().runReadAction(new Computable<TextPainter>() {
      @Override
      public TextPainter compute() {
        return doInitTextPainter(psiFile, editor);
      }
    });
  }

  private static TextPainter doInitTextPainter(final PsiFile psiFile, final Editor editor) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;
    DocumentEx doc = (DocumentEx)PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    if (doc == null) return null;
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(psiFile.getProject(), virtualFile);
    highlighter.setText(doc.getCharsSequence());
    return new TextPainter(doc, highlighter, virtualFile.getPresentableUrl(), virtualFile.getPresentableName(), 
                           psiFile, psiFile.getFileType(), editor);
  }

  private static TextPainter initTextPainter(@NotNull final DocumentEx doc, final Project project) {
    final TextPainter[] res = new TextPainter[1];
    ApplicationManager.getApplication().runReadAction(
      new Runnable() {
        @Override
        public void run() {
          res[0] = doInitTextPainter(doc, project);
        }
      }
    );
    return res[0];
  }

  private static TextPainter doInitTextPainter(@NotNull final DocumentEx doc, Project project) {
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(project, "unknown");
    highlighter.setText(doc.getCharsSequence());
    return new TextPainter(doc, highlighter, "unknown", "unknown", project, FileTypes.PLAIN_TEXT, null);
  }
}
