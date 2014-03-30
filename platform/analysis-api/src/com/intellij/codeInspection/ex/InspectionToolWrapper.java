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
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.lang.Language;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;

/**
 * @author Dmitry Avdeev
 *         Date: 9/28/11
 */
public abstract class InspectionToolWrapper<T extends InspectionProfileEntry, E extends InspectionEP> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionToolWrapper");

  protected T myTool;
  protected final E myEP;

  protected InspectionToolWrapper(@NotNull E ep) {
    this(null, ep);
  }

  protected InspectionToolWrapper(@NotNull T tool) {
    this(tool, null);
  }

  protected InspectionToolWrapper(@Nullable T tool, @Nullable E ep) {
    assert tool != null || ep != null : "must not be both null";
    myEP = ep;
    myTool = tool;
  }

  /** Copy ctor */
  protected InspectionToolWrapper(@NotNull InspectionToolWrapper<T, E> other) {
    myEP = other.myEP;
    // we need to create a copy for buffering
    //noinspection unchecked
    myTool = other.myTool == null ? null : (T)InspectionToolsRegistrarCore.instantiateTool(other.myTool.getClass());
  }

  public void initialize(@NotNull GlobalInspectionContext context) {
    projectOpened(context.getProject());
  }

  @NotNull
  public abstract InspectionToolWrapper<T, E> createCopy();

  @NotNull
  public T getTool() {
    if (myTool == null) {
      //noinspection unchecked
      myTool = (T)myEP.instantiateTool();
      if (!myTool.getShortName().equals(myEP.getShortName())) {
        LOG.error("Short name not matched for " + myTool.getClass() + ": getShortName() = " + myTool.getShortName() + "; ep.shortName = " + myEP.getShortName());
      }
    }
    return myTool;
  }

  public boolean isInitialized() {
    return myTool != null;
  }

  /**
   * @see #applyToDialects()
   * @see #isApplicable(com.intellij.lang.Language)
   */
  @Nullable
  public String getLanguage() {
    return myEP == null ? null : myEP.language;
  }

  public boolean applyToDialects() {
    return myEP != null && myEP.applyToDialects;
  }

  public boolean isApplicable(@NotNull Language language) {
    String langId = getLanguage();
    return langId == null || language.getID().equals(langId) || applyToDialects() && language.isKindOf(langId);
  }

  @NotNull
  public String getShortName() {
    return myEP != null ? myEP.getShortName() : getTool().getShortName();
  }

  @NotNull
  public String getDisplayName() {
    if (myEP == null) {
      return getTool().getDisplayName();
    }
    else {
      String name = myEP.getDisplayName();
      return name == null ? getTool().getDisplayName() : name;
    }
  }

  @NotNull
  public String getGroupDisplayName() {
    if (myEP == null) {
      return getTool().getGroupDisplayName();
    }
    else {
      String groupDisplayName = myEP.getGroupDisplayName();
      return groupDisplayName == null ? getTool().getGroupDisplayName() : groupDisplayName;
    }
  }

  public boolean isEnabledByDefault() {
    return myEP == null ? getTool().isEnabledByDefault() : myEP.enabledByDefault;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return myEP == null ? getTool().getDefaultLevel() : myEP.getDefaultLevel();
  }

  @NotNull
  public String[] getGroupPath() {
    if (myEP == null) {
      return getTool().getGroupPath();
    }
    else {
      String[] path = myEP.getGroupPath();
      return path == null ? getTool().getGroupPath() : path;
    }
  }

  public void projectOpened(@NotNull Project project) {
    if (myEP == null) {
      getTool().projectOpened(project);
    }
  }

  public void projectClosed(@NotNull Project project) {
    if (myEP == null) {
      getTool().projectClosed(project);
    }
  }

  public String getStaticDescription() {
    return myEP == null || myEP.hasStaticDescription ? getTool().getStaticDescription() : null;
  }

  public String loadDescription() {
    final String description = getStaticDescription();
    if (description != null) return description;
    try {
      URL descriptionUrl = getDescriptionUrl();
      if (descriptionUrl == null) return null;
      return ResourceUtil.loadText(descriptionUrl);
    }
    catch (IOException ignored) { }

    return getTool().loadDescription();
  }

  protected URL getDescriptionUrl() {
    Application app = ApplicationManager.getApplication();
    if (myEP == null || app.isUnitTestMode() || app.isHeadlessEnvironment()) {
      return superGetDescriptionUrl();
    }
    String fileName = getDescriptionFileName();
    return myEP.getLoaderForClass().getResource("/inspectionDescriptions/" + fileName);
  }

  @Nullable
  protected URL superGetDescriptionUrl() {
    final String fileName = getDescriptionFileName();
    return ResourceUtil.getResource(getDescriptionContextClass(), "/inspectionDescriptions", fileName);
  }

  @NotNull
  public String getDescriptionFileName() {
    return getShortName() + ".html";
  }

  @NotNull
  public final String getFolderName() {
    return getShortName();
  }

  @NotNull
  public Class<? extends InspectionProfileEntry> getDescriptionContextClass() {
    return getTool().getClass();
  }

  public String getMainToolId() {
    return getTool().getMainToolId();
  }

  public E getExtension() {
    return myEP;
  }

  @Override
  public String toString() {
    return getShortName();
  }

  public void cleanup(Project project) {
    T tool = myTool;
    if (tool != null) {
      tool.cleanup(project);
    }
  }

  @NotNull
  public abstract JobDescriptor[] getJobDescriptors(@NotNull GlobalInspectionContext context);
}
