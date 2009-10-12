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

package com.intellij.util.descriptors;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ConfigFileInfo {
  @NotNull private final ConfigFileMetaData myMetaData;
  @NotNull private final String myUrl;


  public ConfigFileInfo(@NotNull final ConfigFileMetaData metaData, @NotNull final String url) {
    myMetaData = metaData;
    myUrl = url;
  }

  @NotNull
  public ConfigFileMetaData getMetaData() {
    return myMetaData;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ConfigFileInfo that = (ConfigFileInfo)o;

    if (!myMetaData.equals(that.myMetaData)) return false;
    if (!myUrl.equals(that.myUrl)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myMetaData.hashCode();
    result = 31 * result + myUrl.hashCode();
    return result;
  }
}
