// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public class InspectionProfileConvertor {
  private final Map<String, HighlightDisplayLevel> myDisplayLevelMap = new HashMap<>();
  public static final @NonNls String OLD_HIGHTLIGHTING_SETTINGS_PROFILE = "EditorHighlightingSettings";
  public static final @NonNls String OLD_DEFAUL_PROFILE = "OldDefaultProfile";

  private static final Logger LOG = Logger.getInstance(InspectionProfileConvertor.class);

  private static final @NonNls String NAME_ATT = "name";
  private static final @NonNls String VERSION_ATT = "version";
  private static final @NonNls String OPTION_TAG = "option";
  private static final @NonNls String DISPLAY_LEVEL_MAP_OPTION = "DISPLAY_LEVEL_MAP";
  protected static final @NonNls String VALUE_ATT = "value";
  private static final @NonNls String DEFAULT_XML = "Default.xml";
  private static final @NonNls String XML_EXTENSION = ".xml";
  public static final @NonNls String LEVEL_ATT = "level";
  private final InspectionProfileManager myManager;

  public InspectionProfileConvertor(@NotNull InspectionProfileManager manager) {
    myManager = manager;
    renameOldDefaultsProfile();
  }

  private boolean retrieveOldSettings(@NotNull Element element) {
    boolean hasOldSettings = false;
    for (Element option : element.getChildren(OPTION_TAG)) {
      final String name = option.getAttributeValue(NAME_ATT);
      if (name != null) {
        hasOldSettings |= processElement(option, name);
      }
    }
    return hasOldSettings;
  }

  protected boolean processElement(final Element option, final String name) {
    if (name.equals(DISPLAY_LEVEL_MAP_OPTION)) {
      for (Element e : option.getChild(VALUE_ATT).getChildren()) {
        String key = e.getName();
        String levelName = e.getAttributeValue(LEVEL_ATT);
        HighlightSeverity severity = myManager.getSeverityRegistrar().getSeverity(levelName);
        HighlightDisplayLevel level = severity == null ? null : HighlightDisplayLevel.find(severity);
        if (level == null) continue;
        myDisplayLevelMap.put(key, level);
      }
      return true;
    }
    return false;
  }

  public void storeEditorHighlightingProfile(@NotNull Element element, @NotNull InspectionProfileImpl editorProfile) {
    if (retrieveOldSettings(element)) {
      editorProfile.modifyProfile(it -> fillErrorLevels(it));
    }
  }

  private static void renameOldDefaultsProfile() {
    Path directoryPath = Paths.get(PathManager.getConfigPath(), InspectionProfileManager.INSPECTION_DIR);
    if (!Files.exists(directoryPath)) {
      return;
    }

    File[] files = directoryPath.toFile().listFiles(pathname -> pathname.getPath().endsWith(File.separator + DEFAULT_XML));
    if (files == null || files.length != 1 || !files[0].isFile() || files[0].length() == 0) {
      return;
    }
    try {
      Element root = JDOMUtil.load(files[0].toPath());
      if (root.getAttributeValue(VERSION_ATT) == null){
        JDOMUtil.write(root, directoryPath.resolve(OLD_DEFAUL_PROFILE + XML_EXTENSION));
        FileUtil.delete(files[0]);
      }
    }
    catch (IOException | JDOMException e) {
      LOG.error(e);
    }
  }

  protected void fillErrorLevels(final InspectionProfileImpl profile) {
    //noinspection ConstantConditions
    LOG.assertTrue(profile.getInspectionTools(null) != null, "Profile was not correctly init");
    //fill error levels
    for (final String shortName : myDisplayLevelMap.keySet()) {
      //key <-> short name
      HighlightDisplayLevel level = myDisplayLevelMap.get(shortName);

      HighlightDisplayKey key = HighlightDisplayKey.find(shortName);

      if (key == null) continue;

      //set up tools for default profile
      if (level != HighlightDisplayLevel.DO_NOT_SHOW) {
        profile.enableTool(shortName, null);
      }

      if (level == null || level == HighlightDisplayLevel.DO_NOT_SHOW) {
        level = HighlightDisplayLevel.WARNING;
      }
      profile.setErrorLevel(key, level, null);
    }
  }
}
