// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.help.impl;

import javax.help.JHelpContentViewer;
import javax.help.TextHelpModel;

/**
 * It a dirty patch! Help system is so ugly that it hangs when it open some "external" links.
 * To prevent this we open "external" links in nornal WEB browser.
 *
 * @author Vladimir Kondratyev
 */
class IdeaJHelpContentViewer extends JHelpContentViewer{
  /**
   * Creates a JHelp with an specific TextHelpModel as its data model.
   *
   * @param model The TextHelpModel. A null model is valid.
   */
  public IdeaJHelpContentViewer(TextHelpModel model){
    super(model);
  }

  /**
   * PATCHED VERSION OF SUPER METHDO.
   * Replaces the UI with the latest version from the default
   * UIFactory.
   */
  @Override
  public void updateUI(){
    setUI(new IdeaHelpContentViewUI(this));
    invalidate();
  }
}