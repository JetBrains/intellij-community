/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** 
 * @author cdr
 */
public abstract class DocumentAdapter implements DocumentListener {
  public void insertUpdate(DocumentEvent e) {
    textChanged(e);
  }

  public void removeUpdate(DocumentEvent e) {
    textChanged(e);
  }

  public void changedUpdate(DocumentEvent e) {
    textChanged(e);
  }

  protected abstract void textChanged(DocumentEvent e);
}
