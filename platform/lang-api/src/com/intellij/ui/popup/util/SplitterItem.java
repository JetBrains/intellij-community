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
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;

import javax.swing.*;

/**
 * Created with IntelliJ IDEA.
 * User: zajac
 * Date: 11.05.12
 * Time: 21:55
 * To change this template use File | Settings | File Templates.
 */
public class SplitterItem extends ItemWrapper {

  private final String myText;

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
  public void setupRenderer(ColoredTreeCellRenderer renderer, Project project, boolean selected) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void updateAccessoryView(JComponent label) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String speedSearchText() {
    return "";
  }

  @Override
  public String footerText() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  protected void doUpdateDetailView(DetailView panel, boolean editorOnly) {
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
