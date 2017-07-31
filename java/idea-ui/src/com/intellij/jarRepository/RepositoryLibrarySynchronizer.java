/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.jarRepository;

import com.google.common.base.Predicate;
import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;
import org.jetbrains.idea.maven.utils.library.RepositoryUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * @author gregsh
 */
public class RepositoryLibrarySynchronizer implements StartupActivity, DumbAware{
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
      final VirtualFile[] files = library.getFiles(orderRootType);
      final String[] urls = library.getUrls(orderRootType);
      if (files.length != urls.length) {
        return true;
      }
      if (files.length > 1) {
        // check for possible duplicate roots
        final Set<VirtualFile> uniqueFiles = new HashSet<>(Arrays.asList(files));
        if (uniqueFiles.size() != urls.length) {
          return true;
        }
      }
    }
    return false;
  }

  private static Collection<Library> collectLibraries(final @NotNull Project project, final @NotNull Predicate<Library> predicate) {
    final HashSet<Library> result = new HashSet<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      for (final Module module : ModuleManager.getInstance(project).getModules()) {
        OrderEnumerator.orderEntries(module).withoutSdk().forEachLibrary(library -> {
          if (predicate.apply(library)) {
            result.add(library);
          }
          return true;
        });
      }
      for (Library library : ProjectLibraryTable.getInstance(project).getLibraries()) {
        if (predicate.apply(library)) {
          result.add(library);
        }
      }
    });
    return result;
  }

  @Override
  public void runActivity(@NotNull final Project project) {
    final DumbAwareRunnable syncTask = () -> ApplicationManager.getApplication().invokeLater((DumbAwareRunnable)() -> {
      final Collection<Library> toSync = collectLibraries(project, library -> {
        if (library instanceof LibraryEx) {
          final LibraryEx libraryEx = (LibraryEx)library;
          return libraryEx.getProperties() instanceof RepositoryLibraryProperties &&
                 isLibraryNeedToBeReloaded(libraryEx, (RepositoryLibraryProperties)libraryEx.getProperties());
        }
        return false;
      });
      for (Library library : toSync) {
        RepositoryUtils.reloadDependencies(project, (LibraryEx)library);
      }
    }, project.getDisposed());

    project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
      @Override
      public void rootsChanged(final ModuleRootEvent event) {
        if (!myAlarm.isDisposed() && event.getSource() instanceof Project) {
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(syncTask, 300L);
        }
      }
    });

    StartupManager.getInstance(project).registerPostStartupActivity(syncTask);
  }
}
