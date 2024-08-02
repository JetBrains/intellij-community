// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.diagnostic.PluginException;
import com.intellij.l10n.LocalizationUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.ResourceUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dmitry Avdeev
 */
public abstract class InspectionToolWrapper<T extends InspectionProfileEntry, E extends InspectionEP> {
  public static final InspectionToolWrapper[] EMPTY_ARRAY = new InspectionToolWrapper[0];
  private static final String INSPECTION_DESCRIPTIONS_FOLDER = "inspectionDescriptions";

  private static final Logger LOG = Logger.getInstance(InspectionToolWrapper.class);
  private static final Pattern ADDENDUM_PLACE = Pattern.compile("<p><small>New in [\\d.]+</small></p>|(</body>)?\\s*</html>", Pattern.CASE_INSENSITIVE);

  protected T myTool;
  protected final E myEP;
  private @Nullable HighlightDisplayKey myDisplayKey;

  private volatile Set<String> applicableToLanguages; // lazy initialized

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

  public abstract @NotNull InspectionToolWrapper<T, E> createCopy();

  public @NotNull T getTool() {
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
  public @Nullable String getLanguage() {
    return myEP == null ? myTool.getLanguage() : myEP.language;
  }

  public boolean applyToDialects() {
    return myEP != null && myEP.applyToDialects;
  }

  public boolean isApplicable(@NotNull Language language) {
    String myLangId = getLanguage();
    if (myLangId == null || myLangId.isBlank() || "any".equals(myLangId)) return true;

    Set<String> languages = applicableToLanguages;
    if (languages == null) {
      applicableToLanguages = languages = ToolLanguageUtil.getAllMatchingLanguages(myLangId, applyToDialects());
    }

    return languages.contains(language.getID());
  }

  public boolean isCleanupTool() {
    return myEP != null ? myEP.cleanupTool : getTool() instanceof CleanupLocalInspectionTool;
  }

  public @NotNull String getShortName() {
    return myEP != null ? myEP.getShortName() : getTool().getShortName();
  }

  public @Nullable String getDefaultEditorAttributes() {
    return myEP == null ? getTool().getEditorAttributesKey() : myEP.editorAttributes;
  }

  public @NotNull String getID() {
    return getShortName();
  }

  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getDisplayName() {
    if (myEP == null) {
      return getTool().getDisplayName();
    }
    else {
      String name = myEP.getDisplayName();
      return name == null ? getTool().getDisplayName() : name;
    }
  }

  public @NotNull @Nls String getGroupDisplayName() {
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

  public @NotNull HighlightDisplayLevel getDefaultLevel() {
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
    String description = getStaticDescription();
    if (description != null) {
      return description;
    }

    try {
      InputStream descriptionStream = getDescriptionStream();
      if (descriptionStream != null) {
        //noinspection HardCodedStringLiteral(IDEA-249976)
        return insertAddendum(ResourceUtil.loadText(descriptionStream), getTool().getDescriptionAddendum());
      }
      return null;
    }
    catch (IOException ignored) {
    }

    return getTool().loadDescription();
  }

  private static String insertAddendum(String description, HtmlChunk addendum) {
    String addendumString = addendum.toString();
    if (!description.contains("<!-- tooltip end -->")) {
      addendumString = "<!-- tooltip end -->" + addendumString;
    }
    Matcher matcher = ADDENDUM_PLACE.matcher(description);
    if (matcher.find()) {
      return description.substring(0, matcher.start()) + addendumString + description.substring(matcher.start());
    }
    return description + addendumString;
  }

  private @Nullable InputStream getDescriptionStream() {
    String path = INSPECTION_DESCRIPTIONS_FOLDER + "/" + getDescriptionFileName();
    ClassLoader classLoader;
    if (myEP == null) {
      classLoader = getTool().getClass().getClassLoader();
    }
    else {
      classLoader = myEP.getPluginDescriptor().getPluginClassLoader();
    }
    return LocalizationUtil.INSTANCE.getResourceAsStream(classLoader, path, null);
  }

  private @NotNull String getDescriptionFileName() {
    return getShortName() + ".html";
  }

  public final @NotNull String getFolderName() {
    return getShortName();
  }

  public final @NotNull Class<? extends InspectionProfileEntry> getDescriptionContextClass() {
    return getTool().getClass();
  }

  public String getMainToolId() {
    return getTool().getMainToolId();
  }

  public final E getExtension() {
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

  public @Nullable HighlightDisplayKey getDisplayKey() {
    HighlightDisplayKey key = myDisplayKey;
    if (key == null) {
      myDisplayKey = key = HighlightDisplayKey.find(getShortName());
    }
    return key;
  }

  public boolean isApplicable(Set<String> projectTypes) {
    if (myEP == null) return true;

    String projectType = myEP.projectType;
    if (projectType == null) return true;

    return projectTypes.contains(projectType);
  }
}
