package com.intellij.openapi.module.impl;

import com.intellij.CommonBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.module.ProjectLoadingErrorsNotifier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ProjectLoadingErrorsNotifierImpl extends ProjectLoadingErrorsNotifier {
  private final List<ConfigurationErrorDescription> myErrors = new ArrayList<ConfigurationErrorDescription>();
  private final Object myLock = new Object();
  private final Project myProject;

  public ProjectLoadingErrorsNotifierImpl(Project project) {
    myProject = project;
  }

  @Override
  public void registerError(ConfigurationErrorDescription errorDescription) {
    registerErrors(Collections.singletonList(errorDescription));
  }

  @Override
  public void registerErrors(Collection<? extends ConfigurationErrorDescription> errorDescriptions) {
    if (myProject.isDisposed() || myProject.isDefault() || errorDescriptions.isEmpty()) return;

    boolean first;
    synchronized (myLock) {
      first = myErrors.isEmpty();
      myErrors.addAll(errorDescriptions);
    }
    if (myProject.isInitialized()) {
      fireNotifications();
    }
    else if (first) {
      StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          fireNotifications();
        }
      });
    }
  }

  private void fireNotifications() {
    final ConfigurationErrorDescription[] descriptions;
    synchronized (myLock) {
      if (myErrors.isEmpty()) return;
      descriptions = myErrors.toArray(new ConfigurationErrorDescription[myErrors.size()]);
      myErrors.clear();
    }

    final String invalidElements = getInvalidElementsString(descriptions);
    final String errorText = ProjectBundle.message("error.message.configuration.cannot.load") + " " + invalidElements + " <a href=\"\">Fix</a>";

    Notifications.Bus.notify(new Notification("Project Loading Error", "Error Loading Project", errorText, NotificationType.ERROR,
                                              new NotificationListener() {
                                                public void hyperlinkUpdate(@NotNull Notification notification,
                                                                            @NotNull HyperlinkEvent event) {
                                                  final List<ConfigurationErrorDescription> validDescriptions =
                                                    ContainerUtil.findAll(descriptions, new Condition<ConfigurationErrorDescription>() {
                                                      public boolean value(ConfigurationErrorDescription errorDescription) {
                                                        return errorDescription.isValid();
                                                      }
                                                    });
                                                  RemoveInvalidElementsDialog
                                                    .showDialog(myProject, CommonBundle.getErrorTitle(), invalidElements,
                                                                validDescriptions);

                                                  notification.expire();
                                                }
                                              }), myProject);

  }

  private static String getInvalidElementsString(ConfigurationErrorDescription[] descriptions) {
    if (descriptions.length == 1) {
      final ConfigurationErrorDescription description = descriptions[0];
      return description.getElementKind() + " " + description.getElementName();
    }

    TObjectIntHashMap<String> kind2count = new TObjectIntHashMap<String>();
    for (ConfigurationErrorDescription description : descriptions) {
      final String kind = description.getElementKind();
      if (!kind2count.contains(kind)) {
        kind2count.put(kind, 1);
      }
      else {
        kind2count.increment(kind);
      }
    }

    final StringBuilder message = new StringBuilder();
    kind2count.forEachEntry(new TObjectIntProcedure<String>() {
      public boolean execute(String a, int b) {
        if (message.length() > 0) {
          message.append(' ').append(ProjectBundle.message("text.and")).append(' ');
        }
        message.append(b).append(' ').append(b > 1 ? StringUtil.pluralize(a) : a);
        return true;
      }
    });
    return message.toString();
  }
}
