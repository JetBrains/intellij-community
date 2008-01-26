package com.intellij.codeEditor.printing;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.ArrayList;

class MultiFilePainter implements Printable{
  private ArrayList myFilesList;
  private int myFileIndex = 0;
  private int myStartPageIndex = 0;
  private Printable myTextPainter = null;
  private Project myProject;
  private ProgressIndicator myProgress;

  public MultiFilePainter(ArrayList filesList, Project project) {
    myFilesList = filesList;
    myProject = project;
  }

  public void setProgress(ProgressIndicator progress) {
    myProgress = progress;
  }

  public int print(Graphics g, PageFormat pageFormat, int pageIndex) throws PrinterException {
    if (myProgress.isCanceled()) {
      return Printable.NO_SUCH_PAGE;
    }
    while(myFileIndex < myFilesList.size()) {
      if(myTextPainter == null) {
        myTextPainter = PrintManager.initTextPainter((PsiFile)myFilesList.get(myFileIndex), myProject);
      }
      if (myTextPainter != null) {
        ((TextPainter)myTextPainter).setProgress(myProgress);
        int ret = myTextPainter.print(g, pageFormat, pageIndex - myStartPageIndex);
        if (myProgress.isCanceled()) {
          return Printable.NO_SUCH_PAGE;
        }
        if(ret == Printable.PAGE_EXISTS) {
          return Printable.PAGE_EXISTS;
        }
        myTextPainter = null;
        myStartPageIndex = pageIndex;
      }
      myFileIndex++;
    }
    return Printable.NO_SUCH_PAGE;
  }
}