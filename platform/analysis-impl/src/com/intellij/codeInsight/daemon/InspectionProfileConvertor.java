/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.SeverityProvider;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: anna
 * Date: Dec 20, 2004
 */
public class InspectionProfileConvertor {
  private final Map<String, HighlightDisplayLevel> myDisplayLevelMap = new HashMap<>();
  @NonNls public static final String OLD_HIGHTLIGHTING_SETTINGS_PROFILE = "EditorHighlightingSettings";
  @NonNls public static final String OLD_DEFAUL_PROFILE = "OldDefaultProfile";

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettingsConvertor");

  @NonNls private static final String NAME_ATT = "name";
  @NonNls private static final String VERSION_ATT = "version";
  @NonNls private static final String PROFILE_NAME_ATT = "profile_name";
  @NonNls private static final String OPTION_TAG = "option";
  @NonNls private static final String DISPLAY_LEVEL_MAP_OPTION = "DISPLAY_LEVEL_MAP";
  @NonNls protected static final String VALUE_ATT = "value";
  @NonNls private static final String DEFAULT_XML = "Default.xml";
  @NonNls private static final String XML_EXTENSION = ".xml";
  @NonNls public static final String LEVEL_ATT = "level";
  private final SeverityProvider myManager;

  public InspectionProfileConvertor(@NotNull SeverityProvider manager) {
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

  public void storeEditorHighlightingProfile(@NotNull Element element, @NotNull InspectionProfile editorProfile) {
    if (retrieveOldSettings(element)) {
      ModifiableModel editorProfileModel = editorProfile.getModifiableModel();
      fillErrorLevels(editorProfileModel);
      editorProfileModel.commit();
    }
  }

  private static void renameOldDefaultsProfile() {
    String directoryPath = PathManager.getConfigPath() + File.separator + InspectionProfileManager.INSPECTION_DIR;
    File profileDirectory = new File(directoryPath);
    if (!profileDirectory.exists()) {
      return;
    }

    File[] files = profileDirectory.listFiles(pathname -> pathname.getPath().endsWith(File.separator + DEFAULT_XML));
    if (files == null || files.length != 1 || !files[0].isFile()) {
      return;
    }
    try {
      Document doc = JDOMUtil.loadDocument(files[0]);
      Element root = doc.getRootElement();
      if (root.getAttributeValue(VERSION_ATT) == null){
        root.setAttribute(PROFILE_NAME_ATT, OLD_DEFAUL_PROFILE);
        JDOMUtil.writeDocument(doc, new File(profileDirectory, OLD_DEFAUL_PROFILE + XML_EXTENSION), "\n");
        FileUtil.delete(files[0]);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (JDOMException e) {
      LOG.error(e);
    }
  }

  protected void fillErrorLevels(final ModifiableModel profile) {
    InspectionToolWrapper[] toolWrappers = profile.getInspectionTools(null);
    LOG.assertTrue(toolWrappers != null, "Profile was not correctly init");
    //fill error levels
    for (final String shortName : myDisplayLevelMap.keySet()) {
      //key <-> short name
      HighlightDisplayLevel level = myDisplayLevelMap.get(shortName);

      HighlightDisplayKey key = HighlightDisplayKey.find(shortName);

      if (key == null) continue;

      //set up tools for default profile
      if (level != HighlightDisplayLevel.DO_NOT_SHOW) {
        profile.enableTool(shortName, null, null);
      }

      if (level == null || level == HighlightDisplayLevel.DO_NOT_SHOW) {
        level = HighlightDisplayLevel.WARNING;
      }
      profile.setErrorLevel(key, level, null);
    }
  }
}
