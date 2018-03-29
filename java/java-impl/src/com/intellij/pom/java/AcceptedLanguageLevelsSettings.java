// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java;


import com.intellij.notification.*;
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
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@State(
  name = "AcceptedLanguageLevels",
  storages = @Storage("acceptedLanguageLevels.xml")
)
public class AcceptedLanguageLevelsSettings implements PersistentStateComponent<AcceptedLanguageLevelsSettings>, StartupActivity {
  private static final NotificationGroup
    NOTIFICATION_GROUP = new NotificationGroup("Accepted language levels", NotificationDisplayType.STICKY_BALLOON, true);

  @XCollection(propertyElementName = "explicitly-accepted", elementName = "name", valueAttributeName = "")
  public List<String> acceptedNames = ContainerUtil.newArrayList();

  @Override
  public void runActivity(@NotNull Project project) {
    StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> {
      if (!project.isDisposed()) {
        Set<LanguageLevel> usedLevels = new HashSet<>();
        LanguageLevelProjectExtension projectExtension = LanguageLevelProjectExtension.getInstance(project);
        if (projectExtension != null) {
          usedLevels.add(projectExtension.getLanguageLevel());
        }
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          LanguageLevelModuleExtensionImpl moduleExtension = LanguageLevelModuleExtensionImpl.getInstance(module);
          if (moduleExtension != null) {
            ContainerUtil.addIfNotNull(usedLevels, moduleExtension.getLanguageLevel());
          }
        }
        usedLevels.stream()
                  .filter(languageLevel -> !isLanguageLevelAccepted(languageLevel))
                  .map(l -> createNotification(l, project))
                  .forEach(notification -> notification.notify(project));
      }
    });
  }

  public static boolean isLanguageLevelAccepted(LanguageLevel languageLevel) {
    if (!LanguageLevel.HIGHEST.isLessThan(languageLevel)) {
      //officially supported language levels
      return true;
    }
    return getSettings().acceptedNames.contains(languageLevel.name());
  }

  public static void acceptLanguageLevel(LanguageLevel languageLevel) {
    getSettings().acceptedNames.add(languageLevel.name());
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
   * Show legal dialog and set highest accepted level if dialog is rejected
   *
   * @return null if level was rejected
   */
  public static LanguageLevel checkAccepted(Component parent, LanguageLevel level) {
    if (level == null || isLanguageLevelAccepted(level)) {
      return level;
    }
    if (new LegalNoticeDialog(parent, level).showAndGet()) return level;
    return null;
  }

  private static Notification createNotification(LanguageLevel l, Project project) {
    String content = LegalNoticeDialog.getLegalNotice(l);
    content += "<br/>";
    content += "<a href=\'accept\'>Accept</a>";
    content += "&nbsp;&nbsp;";
    content += "<a href=\'decline\'>Decline</a>";
    return NOTIFICATION_GROUP.createNotification(LegalNoticeDialog.EXPERIMENTAL_FEATURE_ALERT, content, NotificationType.WARNING,
                                                 new NotificationListener() {
                                                   @Override
                                                   public void hyperlinkUpdate(@NotNull Notification notification,
                                                                               @NotNull HyperlinkEvent event) {
                                                     if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                                       if ("accept".equals(event.getDescription())) {
                                                         acceptLanguageLevel(l);
                                                       }
                                                       else if ("decline".equals(event.getDescription())) {
                                                         decreaseLanguageLevel(project);
                                                       }
                                                       notification.expire();
                                                     }
                                                   }
                                                 });
  }

  private static void decreaseLanguageLevel(Project project) {
    Messages.showErrorDialog(project, 
                             UIUtil.toHtml("Support for experimental features was rejected.<br/>Language levels would be decreased accordingly"),
                             LegalNoticeDialog.EXPERIMENTAL_FEATURE_ALERT);
    WriteAction.run(() -> {
      LanguageLevel highestAcceptedLevel = getHighestAcceptedLevel();
      JavaProjectModelModificationService service = JavaProjectModelModificationService.getInstance(project);
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        LanguageLevel languageLevel =
          LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
        if (languageLevel != null && !isLanguageLevelAccepted(languageLevel)) {
          LanguageLevel newLanguageLevel = highestAcceptedLevel.isAtLeast(languageLevel) ? LanguageLevel.HIGHEST : highestAcceptedLevel;
          service.changeLanguageLevel(module, newLanguageLevel);
        }
      }

      LanguageLevelProjectExtension projectExtension = LanguageLevelProjectExtension.getInstance(project);
      if (!isLanguageLevelAccepted(projectExtension.getLanguageLevel())) {
        projectExtension.setLanguageLevel(highestAcceptedLevel);
      }
    });
  }
}
