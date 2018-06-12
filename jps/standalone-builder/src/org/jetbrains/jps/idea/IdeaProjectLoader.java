// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.idea;

import groovy.lang.Script;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author max
 */
public class IdeaProjectLoader {
  public static String guessHome(Script script) throws IOException, URISyntaxException {
    String uri = (String)script.getProperty("gant.file");
    File home = new File(new URI(uri).getSchemeSpecificPart());

    while (home != null) {
      if (home.isDirectory() && new File(home, PathMacroUtil.DIRECTORY_STORE_NAME).exists()) {
        return home.getCanonicalPath();
      }


      home = home.getParentFile();
    }


    throw new IllegalArgumentException("Cannot guess project home from '" + uri + "'");
  }
}
