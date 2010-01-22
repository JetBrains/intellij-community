/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.roots.impl;

import com.intellij.ProjectTopics;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

import java.io.IOException;

public class PushedFilePropertiesUpdater {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater");

  private final Project myProject;
  private FilePropertyPusher[] myPushers;
  private FilePropertyPusher[] myFilePushers;

  public static PushedFilePropertiesUpdater getInstance(Project project) {
    return project.getComponent(PushedFilePropertiesUpdater.class);
  }

  public PushedFilePropertiesUpdater(final Project project, final MessageBus bus) {
    myProject = project;
    myPushers = Extensions.getExtensions(FilePropertyPusher.EP_NAME);
    myFilePushers = ContainerUtil.findAllAsArray(myPushers, new Condition<FilePropertyPusher>() {
      public boolean value(FilePropertyPusher pusher) {
        return !pusher.pushDirectoriesOnly();
      }
    });

    ((StartupManagerEx)StartupManager.getInstance(project)).registerPreStartupActivity(new Runnable() {
      public void run() {
        pushAll(myPushers);

        final MessageBusConnection connection = bus.connect();
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
          public void beforeRootsChange(final ModuleRootEvent event) {
          }

          public void rootsChanged(final ModuleRootEvent event) {
            pushAll(myPushers);
            for (FilePropertyPusher pusher : myPushers) {
              pusher.afterRootsChanged(project);
            }
          }
        });

        connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileAdapter() {
          @Override
          public void fileCreated(final VirtualFileEvent event) {
            final VirtualFile file = event.getFile();
            final FilePropertyPusher[] pushers = file.isDirectory() ? myPushers : myFilePushers;
            pushRecursively(file, project, pushers);
          }

          @Override
          public void fileMoved(final VirtualFileMoveEvent event) {
            final VirtualFile file = event.getFile();
            final FilePropertyPusher[] pushers = file.isDirectory()? myPushers : myFilePushers;
            for (FilePropertyPusher pusher : pushers) {
              file.putUserData(pusher.getFileDataKey(), null);
            }
            pushRecursively(file, project, pushers);
          }
        }));
        for (final FilePropertyPusher pusher : myPushers) {
          pusher.initExtra(project, bus, new FilePropertyPusher.Engine() {
            public void pushAll() {
              PushedFilePropertiesUpdater.this.pushAll(pusher);
            }

            public void pushRecursively(VirtualFile file, Project project) {
              PushedFilePropertiesUpdater.this.pushRecursively(file, project, pusher);
            }
          });
        }
      }
    });
  }

  public void pushRecursively(final VirtualFile dir, final Project project, final FilePropertyPusher... pushers) {
    if (pushers.length == 0) return;
    ProjectRootManager.getInstance(project).getFileIndex().iterateContentUnderDirectory(dir, new ContentIterator() {
      public boolean processFile(final VirtualFile fileOrDir) {
        final boolean isDir = fileOrDir.isDirectory();
        for (FilePropertyPusher<Object> pusher : pushers) {
          if (!isDir && (pusher.pushDirectoriesOnly() || !pusher.acceptsFile(fileOrDir))) continue;
          findAndUpdateValue(project, fileOrDir, pusher, null);
        }
        return true;
      }
    });
  }

  private static <T> T findPusherValuesUpwards(final Project project, final VirtualFile dir, FilePropertyPusher<T> pusher, T moduleValue) {
    final T value = pusher.getImmediateValue(project, dir);
    if (value != null) return value;
    if (moduleValue != null) return moduleValue;
    final VirtualFile parent = dir.getParent();
    if (parent != null) return findPusherValuesUpwards(project, parent, pusher);
    return pusher.getDefaultValue();
  }

  private static <T> T findPusherValuesUpwards(final Project project, final VirtualFile dir, FilePropertyPusher<T> pusher) {
    final T userValue = dir.getUserData(pusher.getFileDataKey());
    if (userValue != null) return userValue;
    final T value = pusher.getImmediateValue(project, dir);
    if (value != null) return value;
    final VirtualFile parent = dir.getParent();
    if (parent != null) return findPusherValuesUpwards(project, parent, pusher);
    return pusher.getDefaultValue();
  }

  public void pushAll(final FilePropertyPusher... pushers) {
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      final Object[] moduleValues = new Object[pushers.length];
      for (int i = 0; i < moduleValues.length; i++) {
        moduleValues[i] = pushers[i].getImmediateValue(module);
      }
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      final ModuleFileIndex index = rootManager.getFileIndex();
      for (VirtualFile root : rootManager.getContentRoots()) {
        index.iterateContentUnderDirectory(root, new ContentIterator() {
          public boolean processFile(final VirtualFile fileOrDir) {
            final boolean isDir = fileOrDir.isDirectory();
            for (int i = 0, pushersLength = pushers.length; i < pushersLength; i++) {
              final FilePropertyPusher<Object> pusher = pushers[i];
              if (!isDir && (pusher.pushDirectoriesOnly() || !pusher.acceptsFile(fileOrDir))) continue;
              findAndUpdateValue(myProject, fileOrDir, pusher, moduleValues[i]);
            }
            return true;
          }
        });
      }
    }
  }

  public static <T> void findAndUpdateValue(final Project project, final VirtualFile fileOrDir, final FilePropertyPusher<T> pusher, final T moduleValue) {
    final T value = findPusherValuesUpwards(project, fileOrDir, pusher, moduleValue);
    updateValue(fileOrDir, value, pusher);
  }

  private static <T> void updateValue(final VirtualFile fileOrDir, final T value, final FilePropertyPusher<T> pusher) {
    final T oldValue = fileOrDir.getUserData(pusher.getFileDataKey());
    if (value != oldValue) {
      fileOrDir.putUserData(pusher.getFileDataKey(), value);
      try {
        pusher.persistAttribute(fileOrDir, value);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }
}
