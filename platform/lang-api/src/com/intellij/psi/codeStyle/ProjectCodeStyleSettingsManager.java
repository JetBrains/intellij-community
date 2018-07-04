// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.codeStyle;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.MainConfigurationStateSplitter;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@State(
  name = "ProjectCodeStyleConfiguration",
  storages = @Storage(value = "codeStyles", stateSplitter = ProjectCodeStyleSettingsManager.StateSplitter.class)
)
public class ProjectCodeStyleSettingsManager extends CodeStyleSettingsManager {
  private static final Logger LOG = Logger.getInstance("#" + ProjectCodeStyleSettingsManager.class);

  private static final String MAIN_PROJECT_CODE_STYLE_NAME = "Project";
  private static final String PROJECT_CODE_STYLE_CONFIG_FILE_NAME = "codeStyleConfig";

  private volatile boolean myIsLoaded;
  private static final Object LEGACY_SETTINGS_IMPORT_LOCK = new Object();
  private final Map<String,CodeStyleSettings> mySettingsMap = ContainerUtil.newHashMap();

  private static final NotificationGroup NOTIFICATION_GROUP =
    new NotificationGroup("Code style settings migration", NotificationDisplayType.STICKY_BALLOON, true);

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
            if (!project.isDefault() &&
                !ApplicationManager.getApplication().isUnitTestMode() &&
                !ApplicationManager.getApplication().isHeadlessEnvironment()) {
              saveProjectAndNotify(project);
            }
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

  private static void saveProjectAndNotify(@NotNull Project project) {
    TransactionGuard.submitTransaction(project, () -> {
      project.save();
      Notification notification = new CodeStyleMigrationNotification(project.getName());
      notification.notify(project);
    });
  }

  @Override
  public void setMainProjectCodeStyle(@Nullable CodeStyleSettings settings) {
    // TODO<rv>: Remove the assignment below when there are no direct usages of PER_PROJECT_SETTINGS.
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
      USE_PER_PROJECT_SETTINGS = appCodeStyleSettingsManager.USE_PER_PROJECT_SETTINGS;
      PREFERRED_PROJECT_CODE_STYLE = appCodeStyleSettingsManager.PREFERRED_PROJECT_CODE_STYLE;
    }
    myIsLoaded = true;
  }

  @Override
  public void loadState(@NotNull Element state) {
    super.loadState(state);
    updateFromOldProjectSettings();
    for (Element subStyle : state.getChildren(CodeStyleScheme.CODE_STYLE_TAG_NAME)) {
      String name = subStyle.getAttributeValue(CodeStyleScheme.CODE_STYLE_NAME_ATTR);
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
        Element codeStyle = new Element(CodeStyleScheme.CODE_STYLE_TAG_NAME);
        codeStyle.setAttribute(CodeStyleScheme.CODE_STYLE_NAME_ATTR, name);
        settings.writeExternal(codeStyle);
        if (!codeStyle.getContent().isEmpty()) {
          e.addContent(codeStyle);
        }
      }
    }
    return e;
  }

  private static class CodeStyleMigrationNotification extends Notification {
    CodeStyleMigrationNotification(@NotNull String projectName) {
      super(NOTIFICATION_GROUP.getDisplayId(),
            ApplicationBundle.message("project.code.style.migration.title"),
            ApplicationBundle.message("project.code.style.migration.message", projectName),
            NotificationType.INFORMATION);
      addAction(new ShowMoreInfoAction());
    }
  }

  private static class ShowMoreInfoAction extends DumbAwareAction {
    ShowMoreInfoAction() {
      super("More info");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      BrowserUtil.open("https://confluence.jetbrains.com/display/IDEADEV/New+project+code+style+settings+format+in+2017.3");
    }
  }

  @Override
  protected boolean isIgnoredOnSave(@NotNull String fieldName) {
    return "PER_PROJECT_SETTINGS".equals(fieldName);
  }

  static final class StateSplitter extends MainConfigurationStateSplitter {
    @NotNull
    @Override
    protected String getComponentStateFileName() {
      return PROJECT_CODE_STYLE_CONFIG_FILE_NAME;
    }

    @NotNull
    @Override
    protected String getSubStateTagName() {
      return CodeStyleScheme.CODE_STYLE_TAG_NAME;
    }

    @NotNull
    @Override
    protected String getSubStateFileName(@NotNull Element element) {
      return element.getAttributeValue(CodeStyleScheme.CODE_STYLE_NAME_ATTR);
    }
  }
}
