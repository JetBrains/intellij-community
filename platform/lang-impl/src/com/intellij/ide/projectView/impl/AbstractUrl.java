/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.projectView.impl;

import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public abstract class AbstractUrl {
  protected final String url;
  protected final String moduleName;
  private final String myType;

  protected AbstractUrl(String url, String moduleName, @NonNls String type) {
    myType = type;
    this.url = url == null ? "" : url;
    this.moduleName = moduleName;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void write(Element element) {
    element.setAttribute("url", url);
    if (moduleName != null) {
      element.setAttribute("module", moduleName);
    }
    element.setAttribute("type", myType);
  }

  @Nullable
  public abstract Object[] createPath(Project project);

  // return null if cannot recognize the element
  public AbstractUrl createUrl(String type, String moduleName, String url){
    if (type.equals(myType)) {
      return createUrl(moduleName, url);
    }
    return null;
  }
  protected abstract AbstractUrl createUrl(String moduleName, String url);
  public abstract AbstractUrl createUrlByElement(Object element);


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final AbstractUrl that = (AbstractUrl)o;

    if (moduleName != null ? !moduleName.equals(that.moduleName) : that.moduleName != null) return false;
    if (myType != null ? !myType.equals(that.myType) : that.myType != null) return false;
    if (url != null ? !url.equals(that.url) : that.url != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (url != null ? url.hashCode() : 0);
    result = 29 * result + (moduleName != null ? moduleName.hashCode() : 0);
    result = 29 * result + (myType != null ? myType.hashCode() : 0);
    return result;
  }
}
