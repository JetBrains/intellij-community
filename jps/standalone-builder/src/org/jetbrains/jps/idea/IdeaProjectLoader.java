package org.jetbrains.jps.idea;

import groovy.lang.Script;
import org.jetbrains.jps.gant.JpsGantTool;

/**
 * @author nik
 */
public class IdeaProjectLoader {
//todo[nik] inline this method later
  public static String guessHome(Script script) {
    return JpsGantTool.guessHome(script);
  }
}
