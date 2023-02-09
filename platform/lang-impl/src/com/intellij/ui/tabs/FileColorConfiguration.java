/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.ui.tabs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
class FileColorConfiguration implements Cloneable {
  private static final String COLOR = "color";
  private static final String SCOPE_NAME = "scope";

  private String myScopeName;
  private String myColorID;

  FileColorConfiguration() {
  }

  FileColorConfiguration(final String scopeName, @NonNls String colorID) {
    myScopeName = scopeName;
    myColorID = colorID;
  }

  public String getScopeName() {
    return myScopeName;
  }

  public void setScopeName(String scopeName) {
    myScopeName = scopeName;
  }

  @NonNls
  public String getColorID() {
    return myColorID;
  }

  public void setColorID(@NonNls String colorID) {
    myColorID = colorID;
  }

  public boolean isValid(Project project) {
    if (StringUtil.isEmpty(myScopeName) || myColorID == null) {
      return false;
    }
    return project == null || NamedScopesHolder.getScope(project, myScopeName) != null;
  }

  public void save(@NotNull final Element e) {
    if (!isValid(null)) {
      return;
    }

    final Element tab = new Element(FileColorsModel.FILE_COLOR);

    tab.setAttribute(SCOPE_NAME, getScopeName());
    tab.setAttribute(COLOR, myColorID);

    e.addContent(tab);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FileColorConfiguration that = (FileColorConfiguration)o;

    if (!myColorID.equals(that.myColorID)) return false;
    if (!myScopeName.equals(that.myScopeName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myScopeName.hashCode();
    result = 31 * result + myColorID.hashCode();
    return result;
  }

  @Override
  public FileColorConfiguration clone() throws CloneNotSupportedException {
    final FileColorConfiguration result = new FileColorConfiguration();

    result.myColorID = myColorID;
    result.myScopeName = myScopeName;

    return result;
  }

  @Nullable
  public static FileColorConfiguration load(@NotNull final Element e) {
    final String path = e.getAttributeValue(SCOPE_NAME);
    if (path == null) {
      return null;
    }

    final String colorName = e.getAttributeValue(COLOR);
    if (colorName == null) {
      return null;
    }

    return new FileColorConfiguration(path, colorName);
  }
}
