package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 23, 2004
 */
public class Copy extends Tag {
  public Copy(@NonNls String toDir) {
    //noinspection HardCodedStringLiteral
    super("copy", new Pair[] {new Pair<String, String>("todir", toDir)});
  }
  public Copy(@NonNls String file, @NonNls String toFile) {
    //noinspection HardCodedStringLiteral
    super("copy", new Pair[] {new Pair<String, String>("file", file), new Pair<String, String>("tofile", toFile)});
  }
}
