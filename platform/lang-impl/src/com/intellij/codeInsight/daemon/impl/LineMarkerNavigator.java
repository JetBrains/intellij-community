// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiElement;

import java.awt.event.MouseEvent;

public abstract class LineMarkerNavigator {
  public abstract void browse(MouseEvent e, PsiElement element);
}
