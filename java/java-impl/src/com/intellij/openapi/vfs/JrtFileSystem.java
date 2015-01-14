/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.lang.LangBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class JrtFileSystem extends ArchiveFileSystem {
  public static final String PROTOCOL = "jrt";
  public static final String SEPARATOR = JarFileSystem.JAR_SEPARATOR;

  public JrtFileSystem() {
    scheduleConfiguredSdkCheck();
  }

  private static void scheduleConfiguredSdkCheck() {
    if (isSupported()) return;
    final MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      @Override
      public void appStarting(Project project) {
        for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
          if (getHomePathIfModular(sdk) != null) {
            String title = LangBundle.message("jrt.not.available.title", sdk.getName());
            String message = LangBundle.message("jrt.not.available.message");
            Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, title, message, NotificationType.WARNING));
          }
        }
        connection.disconnect();
      }
    });
  }

  private static String getHomePathIfModular(Sdk sdk) {
    if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
      String homePath = sdk.getHomePath();
      if (homePath != null && isModularJdk(homePath)) {
        return homePath;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getProtocol() {
    return PROTOCOL;
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
    final String homePath = extractLocalPath(extractRootPath(entryFile.getPath()));
    return VfsImplUtil.getHandler(this, homePath + "/lib/modules", new Function<String, ArchiveHandler>() {
      @Override
      public ArchiveHandler fun(String localPath) {
        return isSupported() ? new JrtHandler(homePath) : new JrtHandlerStub(homePath);
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

  public static boolean isSupported() {
    return SystemInfo.isJavaVersionAtLeast("1.8") && !SystemInfo.isJavaVersionAtLeast("1.9");
  }

  public static boolean isModularJdk(@NotNull String homePath) {
    return new File(homePath, "lib/modules").isDirectory();
  }

  public static boolean isRoot(@NotNull VirtualFile file) {
    return file.getParent() == null && file.getFileSystem() instanceof JrtFileSystem;
  }
}
