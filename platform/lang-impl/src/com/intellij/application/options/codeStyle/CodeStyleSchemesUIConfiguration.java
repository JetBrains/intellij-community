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
package com.intellij.application.options.codeStyle;

import com.intellij.openapi.components.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Rustam Vishnyakov
 */
@State(
  name = "CodeStyleSchemesUIConfiguration",
  storages = {@Storage(value = "other.xml", roamingType = RoamingType.DISABLED)}
)
public class CodeStyleSchemesUIConfiguration implements PersistentStateComponent<CodeStyleSchemesUIConfiguration> {
  public String RECENT_IMPORT_FILE_LOCATION = "";

  @Nullable
  @Override
  public CodeStyleSchemesUIConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(CodeStyleSchemesUIConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static CodeStyleSchemesUIConfiguration getInstance() {
    return ServiceManager.getService(CodeStyleSchemesUIConfiguration.class);
  }

  public static class Util {
    @Nullable
    public static VirtualFile getRecentImportFile() {
      CodeStyleSchemesUIConfiguration configuration = getInstance();
      if (configuration != null) {
        String fileLocation = configuration.RECENT_IMPORT_FILE_LOCATION;
        if (fileLocation == null || fileLocation.trim().isEmpty()) return null;
        try {
          URL url = new URL(fileLocation);
          return VfsUtil.findFileByURL(url);
        }
        catch (MalformedURLException e) {
          // Ignore
        }
      }
      return null;
    }

    public static void setRecentImportFile(@NotNull VirtualFile recentFile) {
      CodeStyleSchemesUIConfiguration configuration = getInstance();
      if (configuration != null) {
        URL url = VfsUtilCore.convertToURL(recentFile.getUrl());
        if (url != null) {
          configuration.RECENT_IMPORT_FILE_LOCATION = url.toString();
        }
      }
    }
  }
}
