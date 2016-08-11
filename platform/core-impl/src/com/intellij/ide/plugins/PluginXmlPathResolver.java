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
package com.intellij.ide.plugins;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.xmlb.JDOMXIncluder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * @author nik
 */
class PluginXmlPathResolver implements JDOMXIncluder.PathResolver {
  private final List<File> myPluginJarFiles;

  public PluginXmlPathResolver(File[] filesInLib) {
    myPluginJarFiles = ContainerUtil.filter(filesInLib, new Condition<File>() {
      @Override
      public boolean value(File file) {
        return FileUtil.isJarOrZip(file);
      }
    });
  }

  @NotNull
  @Override
  public URL resolvePath(@NotNull String relativePath, @Nullable String base) {
    URL url = JDOMXIncluder.DEFAULT_PATH_RESOLVER.resolvePath(relativePath, base);
    if (URLUtil.resourceExists(url) == ThreeState.NO) {
      for (File jarFile : myPluginJarFiles) {
        try {
          URL entryURL = URLUtil.getJarEntryURL(jarFile, relativePath);
          if (URLUtil.resourceExists(entryURL) == ThreeState.YES) {
            return entryURL;
          }
        }
        catch (MalformedURLException ignored) {
        }
      }
    }
    return url;
  }
}
