// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

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

  PluginXmlPathResolver(@NotNull File[] filesInLib) {
    myPluginJarFiles = ContainerUtil.filter(filesInLib, FileUtil::isJarOrZip);
  }

  @NotNull
  @Override
  public URL resolvePath(@NotNull String relativePath, @Nullable URL base) throws MalformedURLException {
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
