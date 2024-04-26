// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 * @deprecated Do not use.
 */
@Deprecated(forRemoval = true)
public final class IntelliJLookAndFeelInfo extends UIManager.LookAndFeelInfo {
  public IntelliJLookAndFeelInfo(){
    super(IdeBundle.message("idea.intellij.look.and.feel"), DarculaLaf.class.getName());
  }

  @Override
  public boolean equals(Object obj){
    return (obj instanceof IntelliJLookAndFeelInfo);
  }

  @Override
  public int hashCode(){
    return getName().hashCode();
  }
}