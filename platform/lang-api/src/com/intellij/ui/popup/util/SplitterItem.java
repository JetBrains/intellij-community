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
package com.intellij.ui.popup.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.ui.ColoredListCellRenderer;

import javax.swing.*;

/**
 * Created with IntelliJ IDEA.
 * User: zajac
 * Date: 11.05.12
 * Time: 21:55
 * To change this template use File | Settings | File Templates.
 */
public class SplitterItem implements ItemWrapper {

  private String myText;

  public SplitterItem(String text) {
    myText = text;
  }

  public String getText() {
    return myText;
  }

  @Override
  public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void updateMnemonicLabel(JLabel label) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void execute(Project project, JBPopup popup) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String speedSearchText() {
    return "";  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String footerText() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void updateDetailView(DetailView panel) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean allowedToRemove() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void removed(Project project) {
    //To change body of implemented methods use File | Settings | File Templates.
  }
}
