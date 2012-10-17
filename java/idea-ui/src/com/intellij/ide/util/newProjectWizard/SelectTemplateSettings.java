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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 *         Date: 10/9/12
 */
@State(name = "SelectProjectTemplateSettings", storages = {@Storage( file = StoragePathMacros.APP_CONFIG + "/other.xml")})
public class SelectTemplateSettings implements PersistentStateComponent<SelectTemplateSettings> {

  private static final String STATE_ELEMENT_NAME = "treeState";

  public static SelectTemplateSettings getInstance() {
    return ServiceManager.getService(SelectTemplateSettings.class);
  }

  @Transient
  public TreeState getTreeState() {
    return myTreeState;
  }

  @Transient
  public void setTreeState(TreeState state) {
    myTreeState = state;
  }

  @NotNull
  @Override
  public SelectTemplateSettings getState() {
    try {
      myTreeState.writeExternal(myElement = new Element(STATE_ELEMENT_NAME));
    }
    catch (WriteExternalException ignore) {
    }
    return this;
  }

  @Override
  public void loadState(SelectTemplateSettings state) {
    try {
      myTreeState.readExternal(state.myElement);
    }
    catch (InvalidDataException ignore) {
    }
  }

  @Tag("treeState")
  public Element myElement = new Element(STATE_ELEMENT_NAME);

  private TreeState myTreeState = new TreeState();
}
