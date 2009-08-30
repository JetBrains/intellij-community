package com.intellij.debugger.ui;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.psi.PsiElement;

import javax.swing.*;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public abstract class CompletionEditor extends JComponent{
  public abstract void setText  (TextWithImports text);

  public abstract TextWithImports getText();

  public abstract void setContext(PsiElement context);

  public abstract PsiElement getContext();

  public abstract void dispose();

  public abstract String getRecentsId();
}
