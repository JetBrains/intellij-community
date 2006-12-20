package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class FileSet extends Tag{

  public FileSet(@NonNls final String dir) {
    super("fileset", new Pair[] {pair("dir", dir)});
  }

}
