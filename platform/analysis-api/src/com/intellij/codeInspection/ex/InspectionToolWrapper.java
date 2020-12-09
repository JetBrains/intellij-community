// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.DynamicBundle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.diagnostic.PluginException;
import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.ResourceUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

import static com.intellij.DynamicBundle.findLanguageBundle;

/**
 * @author Dmitry Avdeev
 */
public abstract class InspectionToolWrapper<T extends InspectionProfileEntry, E extends InspectionEP> {
  public static final InspectionToolWrapper[] EMPTY_ARRAY = new InspectionToolWrapper[0];

  private static final Logger LOG = Logger.getInstance(InspectionToolWrapper.class);

  protected T myTool;
  protected final E myEP;
  @Nullable private HighlightDisplayKey myDisplayKey;

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
  protected InspectionToolWrapper(@NotNull InspectionToolWrapper<T, ? extends E> other) {
    myEP = other.myEP;
    // we need to create a copy for buffering
    if (other.myTool == null) {
      myTool = null;
    }
    else {
      //noinspection unchecked
      myTool = (T)(myEP == null ? InspectionToolsRegistrarCore.instantiateTool(other.myTool.getClass()) : myEP.instantiateTool());
    }
  }

  public void initialize(@NotNull GlobalInspectionContext context) {
    getTool().initialize(context);
  }

  @NotNull
  public abstract InspectionToolWrapper<T, E> createCopy();

  @NotNull
  public T getTool() {
    T tool = myTool;
    if (tool == null) {
      //noinspection unchecked
      myTool = tool = (T)myEP.instantiateTool();
      if (!tool.getShortName().equals(myEP.getShortName())) {
        LOG.error(new PluginException("Short name not matched for " + tool.getClass() + ": getShortName() = #" + tool.getShortName() + "; ep.shortName = #" + myEP.getShortName(), myEP.getPluginDescriptor().getPluginId()));
      }
    }
    return tool;
  }

  public boolean isInitialized() {
    return myTool != null;
  }

  /**
   * @see #applyToDialects()
   * @see #isApplicable(Language)
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
    return isApplicable(language, langId);
  }

  private boolean isApplicable(@NotNull Language language, String toolLang) {
    if (toolLang == null) {
      return true;
    }
    if (language.getID().equals(toolLang)) {
      return true;
    }
    if (applyToDialects()) {
      if (language.isKindOf(toolLang)) {
        return true;
      }

      Language toolLanguage = Language.findLanguageByID(toolLang);
      if (toolLanguage instanceof MetaLanguage) {
        for (Language lang : ((MetaLanguage)toolLanguage).getMatchingLanguages()) {
          if (isApplicable(language, lang.getID())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public boolean isCleanupTool() {
    return myEP != null ? myEP.cleanupTool : getTool() instanceof CleanupLocalInspectionTool;
  }

  @NotNull
  public String getShortName() {
    return myEP != null ? myEP.getShortName() : getTool().getShortName();
  }

  public String getID() {
    return getShortName();
  }

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Sentence)
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
  @Nls
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

  public @Nls String @NotNull [] getGroupPath() {
    if (myEP == null) {
      return getTool().getGroupPath();
    }
    else {
      String[] path = myEP.getGroupPath();
      return path == null ? getTool().getGroupPath() : path;
    }
  }

  public @Nls String getStaticDescription() {
    return myEP == null || myEP.hasStaticDescription ? getTool().getStaticDescription() : null;
  }

  public @Nls String loadDescription() {
    final String description = getStaticDescription();
    if (description != null) return description;
    try {
      InputStream descriptionStream = getDescriptionStream();
      //noinspection HardCodedStringLiteral(IDEA-249976)
      return descriptionStream != null ? ResourceUtil.loadText(descriptionStream) : null;
    }
    catch (IOException ignored) { }

    return getTool().loadDescription();
  }

  private InputStream getDescriptionStream() {
    Application app = ApplicationManager.getApplication();
    String fileName = getDescriptionFileName();

    InputStream langStream = getLanguagePluginStream(fileName);
    if (langStream != null) return langStream;

    if (myEP == null || app.isUnitTestMode() || app.isHeadlessEnvironment()) {
      return ResourceUtil.getResourceAsStream(getDescriptionContextClass().getClassLoader(), "inspectionDescriptions", fileName);
    }

    return myEP.getPluginDescriptor().getPluginClassLoader().getResourceAsStream("inspectionDescriptions/" + fileName);
  }

  @Nullable
  private static InputStream getLanguagePluginStream(@NotNull String fileName) {
    DynamicBundle.LanguageBundleEP langBundle = findLanguageBundle();
    if (langBundle == null) return null;

    PluginDescriptor langPluginDescriptor = langBundle.pluginDescriptor;
    if (langPluginDescriptor == null) return null;

    ClassLoader classLoader = langPluginDescriptor.getPluginClassLoader();
    if (classLoader == null) return null;

    return classLoader.getResourceAsStream("inspectionDescriptions/" + fileName);
  }

  @NotNull
  private String getDescriptionFileName() {
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

  public void cleanup(@NotNull Project project) {
    T tool = myTool;
    if (tool != null) {
      tool.cleanup(project);
    }
  }

  public abstract JobDescriptor @NotNull [] getJobDescriptors(@NotNull GlobalInspectionContext context);

  public HighlightDisplayKey getDisplayKey() {
    HighlightDisplayKey key = myDisplayKey;
    if (key == null) {
      myDisplayKey = key = HighlightDisplayKey.find(getShortName());
    }
    return key;
  }
}
