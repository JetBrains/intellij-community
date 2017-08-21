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

package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.MainConfigurationStateSplitter;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.psi.codeStyle.CodeStyleScheme.CODE_STYLE_NAME_ATTR;
import static com.intellij.psi.codeStyle.CodeStyleScheme.CODE_STYLE_TAG_NAME;


@State(
  name = "ProjectCodeStyleConfiguration",
  storages = @Storage(value = "codeStyles", stateSplitter = ProjectCodeStyleSettingsManager.StateSplitter.class)
)
public class ProjectCodeStyleSettingsManager extends CodeStyleSettingsManager {
  private static final Logger LOG = Logger.getInstance("#" + ProjectCodeStyleSettingsManager.class);

  public static final String MAIN_PROJECT_CODE_STYLE_NAME = "Project";
  public static final String PROJECT_CODE_STYLE_CONFIG_FILE_NAME = "codeStyleConfig";

  private volatile boolean myIsLoaded;
  private final static Object LEGACY_SETTINGS_IMPORT_LOCK = new Object();
  private final Map<String,CodeStyleSettings> mySettingsMap = ContainerUtil.newHashMap();

  @SuppressWarnings("unused")
  public ProjectCodeStyleSettingsManager(Project project) {
    this();
  }

  public ProjectCodeStyleSettingsManager() {
    setMainProjectCodeStyle(null);
  }

  void initProjectSettings(@NotNull Project project) {
    if (!myIsLoaded) {
      synchronized (LEGACY_SETTINGS_IMPORT_LOCK) {
        if (!myIsLoaded) {
          LegacyCodeStyleSettingsManager legacySettingsManager = ServiceManager.getService(project, LegacyCodeStyleSettingsManager.class);
          if (legacySettingsManager != null && legacySettingsManager.getState() != null) {
            loadState(legacySettingsManager.getState());
            LOG.info("Imported old project code style settings.");
          }
          else {
            initDefaults();
            LOG.info("Initialized from default code style settings.");
          }
        }
      }
    }
  }

  @Override
  public void setMainProjectCodeStyle(@Nullable CodeStyleSettings settings) {
    // TODO<rv>: Remove the assignment below when there are no direct usages of PER_PROJECT_SETTINGS.
    //noinspection deprecation
    PER_PROJECT_SETTINGS = settings;
    mySettingsMap.put(MAIN_PROJECT_CODE_STYLE_NAME, settings != null ? settings : new CodeStyleSettings());
  }

  @NotNull
  @Override
  public CodeStyleSettings getMainProjectCodeStyle() {
    return mySettingsMap.get(MAIN_PROJECT_CODE_STYLE_NAME);
  }

  private void initDefaults() {
    CodeStyleSettingsManager appCodeStyleSettingsManager = CodeStyleSettingsManager.getInstance();
    if (appCodeStyleSettingsManager != null) {
      CodeStyleSettings defaultProjectSettings = appCodeStyleSettingsManager.getMainProjectCodeStyle();
      setMainProjectCodeStyle(defaultProjectSettings != null ? defaultProjectSettings.clone() : null);
      this.USE_PER_PROJECT_SETTINGS = appCodeStyleSettingsManager.USE_PER_PROJECT_SETTINGS;
      this.PREFERRED_PROJECT_CODE_STYLE = appCodeStyleSettingsManager.PREFERRED_PROJECT_CODE_STYLE;
    }
    myIsLoaded = true;
  }

  @Override
  public void loadState(Element state) {
    super.loadState(state);
    updateFromOldProjectSettings();
    for (Element subStyle : state.getChildren(CODE_STYLE_TAG_NAME)) {
      String name = subStyle.getAttributeValue(CODE_STYLE_NAME_ATTR);
      CodeStyleSettings settings = new CodeStyleSettings();
      settings.readExternal(subStyle);
      if (MAIN_PROJECT_CODE_STYLE_NAME.equals(name)) {
        setMainProjectCodeStyle(settings);
      }
      else {
        mySettingsMap.put(name, settings);
      }
    }
    myIsLoaded = true;
  }

  @SuppressWarnings("deprecation")
  private void updateFromOldProjectSettings() {
    CodeStyleSettings oldProjectSettings = PER_PROJECT_SETTINGS;
    if (oldProjectSettings != null) oldProjectSettings.resetDeprecatedFields();
    setMainProjectCodeStyle(oldProjectSettings);
  }

  @Override
  public Element getState() {
    Element e = super.getState();
    if (e != null) {
      for (String name : mySettingsMap.keySet()) {
        CodeStyleSettings settings = mySettingsMap.get(name);
        Element codeStyle = new Element(CODE_STYLE_TAG_NAME);
        codeStyle.setAttribute(CODE_STYLE_NAME_ATTR, name);
        settings.writeExternal(codeStyle);
        if (!codeStyle.getContent().isEmpty()) {
          e.addContent(codeStyle);
        }
      }
    }
    return e;
  }

  @Override
  protected boolean isIgnoredOnSave(@NotNull String fieldName) {
    return "PER_PROJECT_SETTINGS".equals(fieldName);
  }

  public static final class StateSplitter extends MainConfigurationStateSplitter {

    @NotNull
    @Override
    protected String getComponentStateFileName() {
      return PROJECT_CODE_STYLE_CONFIG_FILE_NAME;
    }

    @NotNull
    @Override
    protected String getSubStateTagName() {
      return CODE_STYLE_TAG_NAME;
    }

    @NotNull
    @Override
    protected String getSubStateFileName(@NotNull Element element) {
      return element.getAttributeValue(CODE_STYLE_NAME_ATTR);
    }
  }
}
