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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;

import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.List;

class MultiFilePainter implements Printable{
  private final List<Pair<PsiFile, Editor>> myFilesList;
  private int myFileIndex = 0;
  private int myStartPageIndex = 0;
  private Printable myTextPainter = null;
  private ProgressIndicator myProgress;

  public MultiFilePainter(List<Pair<PsiFile, Editor>> filesList) {
    myFilesList = filesList;
  }

  public void setProgress(ProgressIndicator progress) {
    myProgress = progress;
  }

  @Override
  public int print(Graphics g, PageFormat pageFormat, int pageIndex) throws PrinterException {
    if (myProgress.isCanceled()) {
      return Printable.NO_SUCH_PAGE;
    }
    while(myFileIndex < myFilesList.size()) {
      if(myTextPainter == null) {
        Pair<PsiFile, Editor> pair = myFilesList.get(myFileIndex);
        myTextPainter = PrintManager.initTextPainter(pair.first, pair.second);
      }
      if (myTextPainter != null) {
        ((TextPainter)myTextPainter).setProgress(myProgress);
        int ret = 0;
        try {
          ret = myTextPainter.print(g, pageFormat, pageIndex - myStartPageIndex);
        }
        catch (ProcessCanceledException ignored) {
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
