/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;

/**
 * User: lex
 * Date: Oct 24, 2003
 * Time: 8:23:06 PM
 */
public abstract class SourcePosition {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.SourcePosition");

  public abstract PsiFile getFile  ();
  public abstract int     getLine  (); //0 - based
  public abstract int     getOffset();

  private abstract static class SourcePositionCache extends SourcePosition {
    private final PsiFile myFile;
    private long myModificationStamp;

    protected int myLine;
    protected int myOffset;

    public SourcePositionCache(PsiFile file) {
      LOG.assertTrue(file != null);
      myFile = file;
      myModificationStamp = myFile.getModificationStamp();
    }

    public PsiFile getFile() {
      return myFile;
    }

    private boolean checkRecalculate() {
      if(myModificationStamp != myFile.getModificationStamp()) {
        myModificationStamp = myFile.getModificationStamp();
        return false;
      } else {
        return true;
      }
    }

    public int getLine() {
      if(checkRecalculate()) {
        myLine = calcLine();
      }
      return myLine;
    }

    public int getOffset() {
      if(checkRecalculate()) {
        myOffset = calcOffset();
      }
      return myOffset;
    }

    protected abstract int calcLine();
    protected abstract int calcOffset();
  }

  public static SourcePosition createFromLine(final PsiFile file, final int line) {
    return new SourcePositionCache(file) {
      protected int calcLine() {
        return line;
      }

      protected int calcOffset() {
        return StringUtil.lineColToOffset(file.getText(), line, 0);
      }
    };
  }

  public static SourcePosition createFromOffset(final PsiFile file, final int offset) {
    return new SourcePositionCache(file) {
      protected int calcLine() {
        final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        return document.getLineNumber(offset);
      }

      protected int calcOffset() {
        return offset;
      }
    };
  }

  public static SourcePosition createFromElement(PsiElement element) {
    PsiElement navigationElement = element.getNavigationElement();
    return createFromOffset(navigationElement.getContainingFile(), navigationElement.getTextOffset());
  }

  public boolean equals(Object o) {
    if(o instanceof SourcePosition) {
      SourcePosition sourcePosition = ((SourcePosition)o);
      return Comparing.equal(sourcePosition.getFile(), getFile()) &&
             sourcePosition.getOffset() == getOffset();
    }

    return false;
  }
}
