package org.jetbrains.jps.idea;

import groovy.lang.Script;

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
      if (home.isDirectory() && new File(home, ".idea").exists()) {
        return home.getCanonicalPath();
      }


      home = home.getParentFile();
    }


    throw new IllegalArgumentException("Cannot guess project home from '" + uri + "'");
  }
}
