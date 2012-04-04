/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.ArrayList;

class MultiFilePainter implements Printable{
  private final ArrayList myFilesList;
  private int myFileIndex = 0;
  private int myStartPageIndex = 0;
  private Printable myTextPainter = null;
  private final Project myProject;
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
        int ret = 0;
        try {
          ret = myTextPainter.print(g, pageFormat, pageIndex - myStartPageIndex);
        }
        catch (ProcessCanceledException e) {
        }

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
