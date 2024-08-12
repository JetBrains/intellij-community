// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractUrl {
  protected final @NotNull String url;
  protected final String moduleName;
  private final String myType;

  protected AbstractUrl(String url, @Nullable String moduleName, @NotNull @NonNls String type) {
    myType = type;
    this.url = StringUtil.notNullize(url);
    this.moduleName = moduleName;
  }

  public @NotNull String getURL() {
    return url;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void write(Element element) {
    element.setAttribute("url", url);
    if (moduleName != null) {
      element.setAttribute("module", moduleName);
    }
    element.setAttribute("type", myType);
  }

  public abstract Object @Nullable [] createPath(Project project);

  public @Nullable("return null if cannot recognize the element") AbstractUrl createUrl(String type, String moduleName, String url){
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
    return myType.equals(that.myType) && url.equals(that.url);
  }

  public int hashCode() {
    int result = url.hashCode();
    result = 29 * result + (moduleName != null ? moduleName.hashCode() : 0);
    result = 29 * result + myType.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ": type=" + myType + ", module=" + moduleName + ", url=" + url;
  }
}
