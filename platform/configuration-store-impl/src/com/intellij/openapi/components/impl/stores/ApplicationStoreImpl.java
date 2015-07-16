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
package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApplicationStoreImpl extends ComponentStoreImpl {
  private static final Logger LOG = Logger.getInstance(ApplicationStoreImpl.class);

  private static final String DEFAULT_STORAGE_SPEC = StoragePathMacros.APP_CONFIG + "/" + PathManager.DEFAULT_OPTIONS_FILE_NAME + DirectoryStorageData.DEFAULT_EXT;
  private static final String ROOT_ELEMENT_NAME = "application";

  private final ApplicationImpl myApplication;
  private final StateStorageManager myStateStorageManager;

  public ApplicationStoreImpl(@NotNull final ApplicationImpl application, @NotNull PathMacroManager pathMacroManager) {
    myApplication = application;
    myStateStorageManager = new StateStorageManagerImpl(pathMacroManager.createTrackingSubstitutor(), ROOT_ELEMENT_NAME, application, application.getPicoContainer()) {
      private boolean myConfigDirectoryRefreshed;

      @NotNull
      @Override
      protected StorageData createStorageData(@NotNull String fileSpec, @NotNull String filePath) {
        return new StorageData(ROOT_ELEMENT_NAME);
      }

      @Nullable
      @Override
      protected String getOldStorageSpec(@NotNull Object component, @NotNull String componentName, @NotNull StateStorageOperation operation) {
        if (component instanceof NamedJDOMExternalizable) {
          return StoragePathMacros.APP_CONFIG + '/' + ((NamedJDOMExternalizable)component).getExternalFileName() + DirectoryStorageData.DEFAULT_EXT;
        }
        else {
          return DEFAULT_STORAGE_SPEC;
        }
      }

      @Override
      protected TrackingPathMacroSubstitutor getMacroSubstitutor(@NotNull String fileSpec) {
        return fileSpec.equals(StoragePathMacros.APP_CONFIG + '/' + PathMacrosImpl.EXT_FILE_NAME + DirectoryStorageData.DEFAULT_EXT) ? null : super.getMacroSubstitutor(fileSpec);
      }

      @Override
      protected boolean isUseXmlProlog() {
        return false;
      }

      @Override
      protected void beforeFileBasedStorageCreate() {
        if (myConfigDirectoryRefreshed || (!application.isUnitTestMode() && !application.isDispatchThread())) {
          return;
        }

        try {
          String configPath = getMacrosValue(StoragePathMacros.ROOT_CONFIG);
          if (configPath == null) {
            LOG.warn("Macros ROOT_CONFIG is not defined");
            return;
          }

          VirtualFile configDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(configPath);
          if (configDir != null) {
            VfsUtil.markDirtyAndRefresh(false, true, true, configDir);
          }
        }
        finally {
          myConfigDirectoryRefreshed = true;
        }
      }
    };
  }

  @Override
  @NotNull
  protected MessageBus getMessageBus() {
    return myApplication.getMessageBus();
  }

  @NotNull
  @Override
  public StateStorageManager getStateStorageManager() {
    return myStateStorageManager;
  }

  @Nullable
  @Override
  protected PathMacroManager getPathMacroManagerForDefaults() {
    return null;
  }
}
