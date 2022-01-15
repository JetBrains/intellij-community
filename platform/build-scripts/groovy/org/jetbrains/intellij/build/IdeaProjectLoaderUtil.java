// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build;

import com.intellij.util.io.URLUtil;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class IdeaProjectLoaderUtil {
  public static String guessHome(Class<?> klass) throws IOException {
    final URL classFileURL = klass.getResource(klass.getSimpleName() + ".class");
    if (classFileURL == null) {
      throw new IllegalStateException("Could not get .class file location from class " + klass.getName());
    }

    File home = URLUtil.urlToFile(classFileURL);

    while (home != null) {
      if (home.isDirectory() && new File(home, PathMacroUtil.DIRECTORY_STORE_NAME).exists()) {
        return home.getCanonicalPath();
      }

      home = home.getParentFile();
    }

    throw new IllegalArgumentException("Cannot guess project home from class '" + klass.getName() + "'");
  }
}
