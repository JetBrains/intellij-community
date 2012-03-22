/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.help.impl;

import com.intellij.util.ui.GraphicsUtil;

import javax.help.DefaultHelpModel;
import javax.help.HelpSet;
import javax.help.JHelp;
import java.awt.*;
import java.util.Vector;

/**
 * It a dirty patch! Help system is so ugly that it hangs when it open some "external" links.
 * To prevent this we open "external" links in nornal WEB browser.
 *
 * @author Vladimir Kondratyev
 */
class IdeaJHelp extends JHelp{
  /**
   * PATCHED VERSION OF SUPER CONSTRUCTOR
   */
  public IdeaJHelp(HelpSet hs){
    super(new DefaultHelpModel(hs));

    navigators=new Vector();
    navDisplayed=true;

    // HERE -- need to do something about doc title changes....

    this.contentViewer=new IdeaJHelpContentViewer(helpModel);

    setModel(helpModel);
    if(helpModel!=null){
      setupNavigators();
    }

    updateUI();
  }

  @Override
  public void paint(Graphics g) {
    GraphicsUtil.setupAntialiasing(g);
    super.paint(g);
  }
}