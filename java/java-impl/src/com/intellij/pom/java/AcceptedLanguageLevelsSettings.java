// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.LegalNoticeDialog;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collection;
import java.util.List;

@State(
  name = "AcceptedLanguageLevels",
  storages = @Storage("acceptedLanguageLevels.xml")
)
public class AcceptedLanguageLevelsSettings implements PersistentStateComponent<AcceptedLanguageLevelsSettings>, StartupActivity {
  private static final NotificationGroup NOTIFICATION_GROUP =
    new NotificationGroup("Accepted language levels", NotificationDisplayType.STICKY_BALLOON, true);

  @XCollection(propertyElementName = "explicitly-accepted", elementName = "name", valueAttributeName = "")
  public List<String> acceptedNames = ContainerUtil.newArrayList();

  @Override
  public void runActivity(@NotNull Project project) {
    StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> {
      if (!project.isDisposed()) {
        MultiMap<LanguageLevel, Module> unacceptedLevels = new MultiMap<>();
        LanguageLevelProjectExtension projectExtension = LanguageLevelProjectExtension.getInstance(project);
        if (projectExtension != null) {
          LanguageLevel level = projectExtension.getLanguageLevel();
          if (!isLanguageLevelAccepted(level)) {
            unacceptedLevels.putValue(level, null);
          }
        }
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          LanguageLevelModuleExtensionImpl moduleExtension = LanguageLevelModuleExtensionImpl.getInstance(module);
          if (moduleExtension != null) {
            LanguageLevel level = moduleExtension.getLanguageLevel();
            if (level != null && !isLanguageLevelAccepted(level)) {
              unacceptedLevels.putValue(level, module);
            }
          }
        }
        if (!unacceptedLevels.isEmpty()) {
          decreaseLanguageLevel(project);

          for (LanguageLevel level : unacceptedLevels.keySet()) {
            NOTIFICATION_GROUP.createNotification(
              EXPERIMENTAL_FEATURE_ALERT,
              getLegalNotice(level) + "<br/><a href='accept'>Accept</a>",
              NotificationType.WARNING,
              (notification, event) -> {
                if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                  switch (event.getDescription()) {
                    case "accept":
                      acceptAndRestore(project, unacceptedLevels.get(level), level);
                      break;
                  }
                  notification.expire();
                }
              }).notify(project);
          }
        }
      }
    });
  }

  public static boolean isLanguageLevelAccepted(LanguageLevel languageLevel) {
    //allow custom features to appear in EAP
    if (ApplicationManager.getApplication().isEAP()) return true;
    // language levels up to HIGHEST are officially supported
    return LanguageLevel.HIGHEST.compareTo(languageLevel) >= 0 || getSettings().acceptedNames.contains(languageLevel.name());
  }

  private static void acceptAndRestore(Project project, Collection<Module> modules, LanguageLevel languageLevel) {
    if (!getSettings().acceptedNames.contains(languageLevel.name())) {
      getSettings().acceptedNames.add(languageLevel.name());
    }

    if (modules != null) {
      JavaProjectModelModificationService service = JavaProjectModelModificationService.getInstance(project);
      WriteAction.run(() -> {
        for (Module module : modules) {
          if (module != null) {
            service.changeLanguageLevel(module, languageLevel);
          }
          else {
            LanguageLevelProjectExtension projectExtension = LanguageLevelProjectExtension.getInstance(project);
            projectExtension.setLanguageLevel(languageLevel);
            projectExtension.setDefault(false);
          }
        }
      });
    }
  }

  public static LanguageLevel getHighestAcceptedLevel() {
    LanguageLevel highest = LanguageLevel.HIGHEST;
    for (LanguageLevel level : LanguageLevel.values()) {
      if (isLanguageLevelAccepted(level)) {
        highest = level;
      }
      else {
        break;
      }
    }
    return highest;
  }

  private static AcceptedLanguageLevelsSettings getSettings() {
    return ServiceManager.getService(AcceptedLanguageLevelsSettings.class);
  }

  @Nullable
  @Override
  public AcceptedLanguageLevelsSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull AcceptedLanguageLevelsSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  /**
   * Shows a legal notice dialog and downgrades to the highest accepted level if the dialog is rejected.
   *
   * @return {@code null} if level was rejected
   */
  public static LanguageLevel checkAccepted(Component parent, LanguageLevel level) {
    return level == null || isLanguageLevelAccepted(level) || showDialog(parent, level) ? level : null;
  }

  private static boolean showDialog(Component parent, LanguageLevel level) {
    int result = LegalNoticeDialog.build(EXPERIMENTAL_FEATURE_ALERT, getLegalNotice(level)).withParent(parent).show();
    if (result == DialogWrapper.OK_EXIT_CODE) {
      acceptAndRestore(null, null, level);
      return true;
    }
    else {
      return false;
    }
  }

  private static void decreaseLanguageLevel(Project project) {
    WriteAction.run(() -> {
      LanguageLevel highestAcceptedLevel = getHighestAcceptedLevel();
      JavaProjectModelModificationService service = JavaProjectModelModificationService.getInstance(project);
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        LanguageLevel languageLevel = LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
        if (languageLevel != null && !isLanguageLevelAccepted(languageLevel)) {
          LanguageLevel newLanguageLevel = highestAcceptedLevel.isAtLeast(languageLevel) ? LanguageLevel.HIGHEST : highestAcceptedLevel;
          service.changeLanguageLevel(module, newLanguageLevel);
        }
      }

      LanguageLevelProjectExtension projectExtension = LanguageLevelProjectExtension.getInstance(project);
      if (!isLanguageLevelAccepted(projectExtension.getLanguageLevel())) {
        projectExtension.setLanguageLevel(highestAcceptedLevel);
        projectExtension.setDefault(false);
      }
    });
  }

  private static final String EXPERIMENTAL_FEATURE_ALERT = "Experimental Feature Alert";

  private static String getLegalNotice(LanguageLevel level) {
    return
      "You must accept the terms of legal notice of the beta Java specification to enable support for " +
      "\"" + StringUtil.substringAfter(level.getPresentableText(), " - ") + "\".<br/><br/>" +
      "<b>The implementation of an early-draft specification developed under the Java Community Process (JCP) " +
      "is made available for testing and evaluation purposes only and is not compatible with any specification of the JCP.</b>";
  }
}