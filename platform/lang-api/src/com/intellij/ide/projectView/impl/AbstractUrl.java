// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApiStatus.NonExtendable
public abstract class AbstractUrl {
  @ApiStatus.Internal
  public static final @NonNls String TYPE_DIRECTORY = "directory";
  @ApiStatus.Internal
  public static final @NonNls String TYPE_LIBRARY_MODULE_GROUP = "libraryModuleGroup";
  @ApiStatus.Internal
  public static final @NonNls String TYPE_MODULE_GROUP = "module_group";
  @ApiStatus.Internal
  public static final @NonNls String TYPE_MODULE = "module";
  @ApiStatus.Internal
  public static final @NonNls String TYPE_NAMED_LIBRARY = "namedLibrary";
  @ApiStatus.Internal
  public static final @NonNls String TYPE_PSI_FILE = "psiFile";

  protected final @NotNull String url;
  protected final String moduleName;
  private final String myType;

  protected AbstractUrl(String url, @Nullable String moduleName, @NotNull @NonNls String type) {
    myType = type;
    this.url = StringUtil.notNullize(url);
    this.moduleName = moduleName;
    ourAbstractUrlProviders.add(this);
  }

  public @NotNull String getURL() {
    return url;
  }

  public @NotNull String getType() {
    return myType;
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


  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final AbstractUrl that = (AbstractUrl)o;

    if (moduleName != null ? !moduleName.equals(that.moduleName) : that.moduleName != null) return false;
    return myType.equals(that.myType) && url.equals(that.url);
  }

  @Override
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

  private static final List<AbstractUrl> ourAbstractUrlProviders = Collections.synchronizedList(new ArrayList<>());

  @ApiStatus.Internal
  public static List<AbstractUrl> getAbstractUrlProviders() {
    ApplicationManager.getApplication().getService(AbstractUrlLoader.class).loadUrls();
    return ourAbstractUrlProviders;
  }

  @ApiStatus.Internal
  public interface AbstractUrlLoader {
    void loadUrls();
  }
}
