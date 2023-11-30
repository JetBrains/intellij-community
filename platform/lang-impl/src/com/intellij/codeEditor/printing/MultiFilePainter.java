// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeEditor.printing;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiFile;

import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.List;

final class MultiFilePainter extends BasePainter {
  private final List<? extends PsiFile> myFilesList;
  private final boolean myEvenNumberOfPagesPerFile;
  private int myFileIndex = 0;
  private int myStartPageIndex = 0;
  private TextPainter myTextPainter = null;
  private int myLargestPrintedPage = -1;

  MultiFilePainter(List<? extends PsiFile> filesList, boolean evenNumberOfPagesPerFile) {
    myFilesList = filesList;
    myEvenNumberOfPagesPerFile = evenNumberOfPagesPerFile;
  }

  @Override
  public int print(Graphics g, PageFormat pageFormat, int pageIndex) throws PrinterException {
    if (myProgress.isCanceled()) {
      return Printable.NO_SUCH_PAGE;
    }
    while (myFileIndex < myFilesList.size()) {
      if (myTextPainter == null) {
        PsiFile psiFile = myFilesList.get(myFileIndex);
        myTextPainter = TextPrintHandler.initTextPainter(psiFile);
      }
      if (myTextPainter != null) {
        myTextPainter.setProgress(myProgress);

        int ret = 0;
        try {
          ret = myTextPainter.print(g, pageFormat, pageIndex - myStartPageIndex);
        }
        catch (ProcessCanceledException ignored) { }

        if (myProgress.isCanceled()) {
          return Printable.NO_SUCH_PAGE;
        }
        if (ret == Printable.PAGE_EXISTS) {
          myLargestPrintedPage = pageIndex;
          return Printable.PAGE_EXISTS;
        }
        if (myEvenNumberOfPagesPerFile && pageIndex == (myLargestPrintedPage + 1) && (pageIndex % 2) == 1 &&
            myFileIndex < (myFilesList.size() - 1)) {
          return PAGE_EXISTS;
        }
        myTextPainter.dispose();
        myTextPainter = null;
        myStartPageIndex = pageIndex;
      }
      myFileIndex++;
      myLargestPrintedPage = -1;
    }
    return Printable.NO_SUCH_PAGE;
  }

  @Override
  void dispose() {
    if (myTextPainter != null) {
      myTextPainter.dispose();
      myTextPainter = null;
    }
  }
}
