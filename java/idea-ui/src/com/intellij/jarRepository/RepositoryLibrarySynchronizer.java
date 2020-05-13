// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository;

import com.intellij.ProjectTopics;
import com.intellij.ide.JavaUiBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;
import org.jetbrains.idea.maven.utils.library.RepositoryUtils;

import java.util.*;
import java.util.function.Predicate;

import static com.intellij.jarRepository.SyncLoadingKt.loadDependenciesSync;

/**
 * @author gregsh
 */
public class RepositoryLibrarySynchronizer implements StartupActivity.DumbAware {

  private static boolean isLibraryNeedToBeReloaded(LibraryEx library, RepositoryLibraryProperties properties) {
    String version = properties.getVersion();
    if (version == null) {
      return false;
    }
    if (version.equals(RepositoryLibraryDescription.LatestVersionId)
        || version.equals(RepositoryLibraryDescription.ReleaseVersionId)
        || version.endsWith(RepositoryLibraryDescription.SnapshotVersionSuffix)) {
      return true;
    }
    for (OrderRootType orderRootType : OrderRootType.getAllTypes()) {
      if (library.getFiles(orderRootType).length != library.getUrls(orderRootType).length) {
        return true;
      }
    }
    return false;
  }

  public static Set<Library> collectLibraries(@NotNull final Project project, @NotNull final Predicate<? super Library> predicate) {
    final Set<Library> result = new LinkedHashSet<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      if (project.isDisposed()) return;
      
      for (final Module module : ModuleManager.getInstance(project).getModules()) {
        OrderEnumerator.orderEntries(module).withoutSdk().forEachLibrary(library -> {
          if (predicate.test(library)) {
            result.add(library);
          }
          return true;
        });
      }
      for (Library library : LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraries()) {
        if (predicate.test(library)) {
          result.add(library);
        }
      }
    });
    return result;
  }

  private static void removeDuplicatedUrlsFromRepositoryLibraries(@NotNull Project project) {
    Collection<Library> libraries = collectLibraries(project, library ->
      library instanceof LibraryEx && ((LibraryEx)library).getProperties() instanceof RepositoryLibraryProperties && hasDuplicatedRoots(library)
    );

    if (!libraries.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        List<Library> validLibraries = ContainerUtil.filter(libraries, LibraryTableImplUtil::isValidLibrary);
        if (validLibraries.isEmpty()) return;

        WriteAction.run(() -> {
          for (Library library : validLibraries) {
            Library.ModifiableModel model = library.getModifiableModel();
            for (OrderRootType type : OrderRootType.getAllTypes()) {
              String[] urls = model.getUrls(type);
              Set<String> uniqueUrls = new LinkedHashSet<>(Arrays.asList(urls));
              if (uniqueUrls.size() != urls.length) {
                for (String url : urls) {
                  model.removeRoot(url, type);
                }
                for (String url : uniqueUrls) {
                  model.addRoot(url, type);
                }
              }
            }
            model.commit();
          }
        });
        String libraryText = validLibraries.size() == 1
                             ? "'" + LibraryUtil.getPresentableName(validLibraries.iterator().next()) + "' library"
                             : validLibraries.size() + " libraries";
        Notifications.Bus.notify(new Notification(
          "Repository", JavaUiBundle.message("notification.title.repository.libraries.cleanup"),
          JavaUiBundle.message("notification.text.duplicated.urls.were.removed", libraryText, ApplicationNamesInfo.getInstance().getFullProductName()),
          NotificationType.INFORMATION
        ), project);
      }, project.getDisposed());
    }
  }

  private static boolean hasDuplicatedRoots(Library library) {
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      String[] urls = library.getUrls(type);
      if (urls.length != ContainerUtil.set(urls).size()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void runActivity(@NotNull final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      loadDependenciesSync(project);
      return;
    }

    final Runnable syncTask = () -> {
      final Collection<Library> toSync = collectLibrariesToSync(project);
      ApplicationManager.getApplication().invokeLater(() -> {
        for (Library library : toSync) {
          if (LibraryTableImplUtil.isValidLibrary(library)) {
            RepositoryUtils.reloadDependencies(project, (LibraryEx)library);
          }
        }
      }, project.getDisposed());

    };

    project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);

      @Override
      public void rootsChanged(@NotNull final ModuleRootEvent event) {
        if (!myAlarm.isDisposed() && event.getSource() instanceof Project) {
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(syncTask, 300L);
        }
      }
    });
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      removeDuplicatedUrlsFromRepositoryLibraries(project);
      syncTask.run();
    });
  }

  @NotNull
  public static Set<Library> collectLibrariesToSync(@NotNull Project project) {
    return collectLibraries(project, library -> {
      if (library instanceof LibraryEx) {
        final LibraryEx libraryEx = (LibraryEx)library;
        return libraryEx.getProperties() instanceof RepositoryLibraryProperties &&
               isLibraryNeedToBeReloaded(libraryEx, (RepositoryLibraryProperties)libraryEx.getProperties());
      }
      return false;
    });
  }
}
