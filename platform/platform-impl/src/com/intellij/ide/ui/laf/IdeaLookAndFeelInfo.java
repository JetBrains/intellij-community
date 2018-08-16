// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
* @author Konstantin Bulenkov
*/
final class IdeaLookAndFeelInfo extends UIManager.LookAndFeelInfo {
  @NonNls public static final String CLASS_NAME = "idea.laf.classname";
  public IdeaLookAndFeelInfo(){
    super(IdeBundle.message("idea.default.look.and.feel"), CLASS_NAME);
  }

  @Override
  public boolean equals(Object obj){
    return (obj instanceof IdeaLookAndFeelInfo);
  }

  @Override
  public int hashCode(){
    return getName().hashCode();
  }
}
