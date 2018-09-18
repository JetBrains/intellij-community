// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.IdeBundle;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public final class IntelliJLookAndFeelInfo extends UIManager.LookAndFeelInfo {
  public IntelliJLookAndFeelInfo(){
    super(IdeBundle.message("idea.intellij.look.and.feel"), IntelliJLaf.class.getName());
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