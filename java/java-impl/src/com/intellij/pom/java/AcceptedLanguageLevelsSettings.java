// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.LegalNoticeDialog;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.List;
import java.util.*;

@State(
  name = "AcceptedLanguageLevels",
  storages = @Storage("acceptedLanguageLevels.xml")
)
public class AcceptedLanguageLevelsSettings implements PersistentStateComponent<AcceptedLanguageLevelsSettings>, StartupActivity {
  private static final NotificationGroup NOTIFICATION_GROUP =
    new NotificationGroup("Accepted language levels", NotificationDisplayType.STICKY_BALLOON, true);

  private static final NotificationGroup PREVIEW_NOTIFICATION_GROUP = NotificationGroup.balloonGroup("Java Preview Features");
  private static final String IGNORE_USED_PREVIEW_FEATURES = "ignore.preview.features.used";

  @XCollection(propertyElementName = "explicitly-accepted", elementName = "name", valueAttributeName = "")
  public List<String> acceptedNames = new ArrayList<>();

  @Override
  public void runActivity(@NotNull Project project) {
    StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> {
      if (!project.isDisposed()) {
        TreeSet<LanguageLevel> previewLevels = new TreeSet<>();
        MultiMap<LanguageLevel, Module> unacceptedLevels = new MultiMap<>();
        LanguageLevelProjectExtension projectExtension = LanguageLevelProjectExtension.getInstance(project);
        if (projectExtension != null) {
          LanguageLevel level = projectExtension.getLanguageLevel();
          if (!isLanguageLevelAccepted(level)) {
            unacceptedLevels.putValue(level, null);
          }
          if (level.isPreview()) {
            previewLevels.add(level);
          }
        }
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          LanguageLevelModuleExtensionImpl moduleExtension = LanguageLevelModuleExtensionImpl.getInstance(module);
          if (moduleExtension != null) {
            LanguageLevel level = moduleExtension.getLanguageLevel();
            if (level != null) {
              if (!isLanguageLevelAccepted(level)) {
                unacceptedLevels.putValue(level, module);
              }
              if (level.isPreview()) {
                previewLevels.add(level);
              }
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
                  if (event.getDescription().equals("accept")) {
                    acceptAndRestore(project, unacceptedLevels.get(level), level);
                  }
                  notification.expire();
                }
              }).notify(project);
          }
        }
        if (!previewLevels.isEmpty() &&
            !PropertiesComponent.getInstance(project).getBoolean(IGNORE_USED_PREVIEW_FEATURES, false)) {
          Optional<LanguageLevel> languageLevel = previewLevels.stream().min(Comparator.naturalOrder());
          assert languageLevel.isPresent();
          int previewFeature = languageLevel.get().toJavaVersion().feature;
          String content = String
            .format("When Java %d would be released, support for %d (Preview) language level may be dropped", previewFeature + 1, previewFeature);
          Notification notification = PREVIEW_NOTIFICATION_GROUP.createNotification("Java Preview Features",
                                                                                    "Newer IDE versions may discontinue support for preview features",
                                                                                    content,
                                                                                    NotificationType.WARNING);
          notification.addAction(new NotificationAction("Do Not Show Again") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
              PropertiesComponent.getInstance(project).setValue(IGNORE_USED_PREVIEW_FEATURES,true);
              notification.expire();
            }
          });
          notification.notify(project);
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

  private static void acceptAndRestore(Project project, Collection<? extends Module> modules, LanguageLevel languageLevel) {
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
      "\"" + level.getPresentableText() + "\".<br/><br/>" +
      "<b>The implementation of an early-draft specification developed under the Java Community Process (JCP) " +
      "is made available for testing and evaluation purposes only and is not compatible with any specification of the JCP.</b>";
  }

  @TestOnly
  public static void allowLevel(@NotNull Disposable parentDisposable, @NotNull LanguageLevel level) {
    List<String> acceptedNames = getSettings().acceptedNames;
    String name = level.name();
    if (!acceptedNames.contains(name)) {
      acceptedNames.add(name);
      Disposer.register(parentDisposable, () -> getSettings().acceptedNames.remove(name));
    }
  }
}