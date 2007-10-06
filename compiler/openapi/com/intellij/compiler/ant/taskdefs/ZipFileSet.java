package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class ZipFileSet extends Tag{
  public static final ZipFileSet[] EMPTY_ARRAY = new ZipFileSet[0];

  public ZipFileSet(@NonNls String fileOrDir, @NonNls final String relativePath, boolean isDir) {
    super("zipfileset", new Pair[] {
      pair(isDir ? "dir" : "file", fileOrDir),
      pair("prefix", prefix(isDir, relativePath))});
  }

  private static String prefix(final boolean isDir, final String relativePath) {
    String path;
    if (isDir) {
      path = relativePath;
    }
    else {
      final String parent = new File(relativePath).getParent();
      path = parent == null ? "" : FileUtil.toSystemIndependentName(parent);
    }
    return path == null ? null : StringUtil.trimStart(path, "/");
  }

}
