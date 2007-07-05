/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.javaee;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NonNls;

/**
 * author: lesya
 */
public abstract class ExternalResourceManager {
  public static ExternalResourceManager getInstance() {
    return ApplicationManager.getApplication().getComponent(ExternalResourceManager.class);
  }

  public abstract void addResource(@NonNls String url, @NonNls String location);
  public abstract void addResource(@NonNls String url, @NonNls String version, @NonNls String location);
  public abstract void removeResource(String url);
  public abstract void removeResource(String url, String version);

  public abstract void addStdResource(@NonNls String resource, @NonNls String fileName);
  public abstract void addStdResource(@NonNls String resource, @NonNls String fileName, Class klass);
  public abstract void addStdResource(@NonNls String resource, @NonNls String version, @NonNls String fileName, Class klass);

  public abstract String getResourceLocation(@NonNls String url);
  public abstract String getResourceLocation(@NonNls String url, String version);
  public abstract String[] getResourceUrls(FileType fileType, final boolean includeStandard);
  public abstract String[] getResourceUrls(FileType fileType, @NonNls String version, final boolean includeStandard);
}
