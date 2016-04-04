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
package com.intellij.openapi.vfs.impl.jrt;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.lang.LangBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.util.containers.ContainerUtil.newTroveMap;

public class JrtFileSystem extends ArchiveFileSystem {
  public static final String PROTOCOL = StandardFileSystems.JRT_PROTOCOL;
  public static final String SEPARATOR = JarFileSystem.JAR_SEPARATOR;

  private static final boolean SUPPORTED =
    SystemInfo.isJavaVersionAtLeast("9") || SystemInfo.isJavaVersionAtLeast("1.8") && !SystemInfo.isJavaVersionAtLeast("1.9");

  private final Map<String, ArchiveHandler> myHandlers = newTroveMap(FileUtil.PATH_HASHING_STRATEGY);
  private final AtomicBoolean mySubscribed = new AtomicBoolean(false);

  public JrtFileSystem() {
    scheduleConfiguredSdkCheck();
  }

  private static void scheduleConfiguredSdkCheck() {
    if (isSupported()) return;
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      @Override
      public void appStarting(Project project) {
        for (Sdk sdk : ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())) {
          String homePath = sdk.getHomePath();
          if (homePath != null && isModularJdk(homePath)) {
            String title = LangBundle.message("jrt.not.available.title", sdk.getName());
            String message = LangBundle.message("jrt.not.available.message");
            Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, title, message, NotificationType.WARNING));
          }
        }
        connection.disconnect();
      }
    });
  }

  @NotNull
  @Override
  public String getProtocol() {
    return PROTOCOL;
  }

  @Nullable
  @Override
  protected String normalize(@NotNull String path) {
    int p = path.indexOf(SEPARATOR);
    return p > 0 ? FileUtil.normalize(path.substring(0, p)) + path.substring(p) : super.normalize(path);
  }

  @NotNull
  @Override
  protected String extractLocalPath(@NotNull String rootPath) {
    return StringUtil.trimEnd(rootPath, SEPARATOR);
  }

  @NotNull
  @Override
  protected String composeRootPath(@NotNull String localPath) {
    return localPath + SEPARATOR;
  }

  @NotNull
  @Override
  protected String extractRootPath(@NotNull String entryPath) {
    int separatorIndex = entryPath.indexOf(SEPARATOR);
    assert separatorIndex >= 0 : "Path passed to JrtFileSystem must have a separator '!/': " + entryPath;
    return entryPath.substring(0, separatorIndex + SEPARATOR.length());
  }

  @NotNull
  @Override
  protected ArchiveHandler getHandler(@NotNull VirtualFile entryFile) {
    checkSubscription();

    String homePath = extractLocalPath(extractRootPath(entryFile.getPath()));
    ArchiveHandler handler = myHandlers.get(homePath);
    if (handler == null) {
      handler = isSupported() ? new JrtHandler(homePath) : new JrtHandlerStub(homePath);
      myHandlers.put(homePath, handler);
      ApplicationManager.getApplication().invokeLater(() -> {
        VirtualFile modules = LocalFileSystem.getInstance().refreshAndFindFileByPath(homePath + "/lib/modules");
        if (modules != null && modules.isDirectory()) modules.getChildren();
      }, ModalityState.defaultModalityState());
    }
    return handler;
  }

  private void checkSubscription() {
    if (mySubscribed.getAndSet(true)) return;

    Application app = ApplicationManager.getApplication();
    app.getMessageBus().connect(app).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        Set<VirtualFile> toRefresh = null;

        for (VFileEvent event : events) {
          if (event.getFileSystem() instanceof LocalFileSystem && event instanceof VFileContentChangeEvent) {
            VirtualFile file = event.getFile();
            if (file != null) {
              String homePath = null;
              if ("modules".equals(file.getName())) homePath = file.getParent().getParent().getPath();
              else if ("jimage".equals(file.getExtension())) homePath = file.getParent().getParent().getParent().getPath();
              if (homePath != null && myHandlers.remove(homePath) != null) {
                VirtualFile root = findFileByPath(composeRootPath(homePath));
                if (root != null) {
                  ((NewVirtualFile)root).markDirtyRecursively();
                  if (toRefresh == null) toRefresh = ContainerUtil.newHashSet();
                  toRefresh.add(root);
                }
              }
            }
          }
        }

        if (toRefresh != null) {
          boolean async = !ApplicationManager.getApplication().isUnitTestMode();
          RefreshQueue.getInstance().refresh(async, true, null, toRefresh);
        }
      }
    });
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return VfsImplUtil.findFileByPath(this, path);
  }

  @Override
  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    return VfsImplUtil.findFileByPathIfCached(this, path);
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return VfsImplUtil.refreshAndFindFileByPath(this, path);
  }

  @Override
  public void refresh(boolean asynchronous) {
    VfsImplUtil.refresh(this, asynchronous);
  }

  @Override
  protected boolean isCorrectFileType(@NotNull VirtualFile local) {
    return isModularJdk(FileUtil.toSystemDependentName(local.getPath()));
  }

  public static boolean isSupported() {
    return SUPPORTED;
  }

  public static boolean isModularJdk(@NotNull String homePath) {
    return new File(homePath, "lib/modules").exists() && new File(homePath, "jrt-fs.jar").isFile();
  }

  public static boolean isRoot(@NotNull VirtualFile file) {
    return file.getParent() == null && file.getFileSystem() instanceof JrtFileSystem;
  }
}