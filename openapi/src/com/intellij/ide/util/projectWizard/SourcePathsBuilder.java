package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 20, 2007
 */
public interface SourcePathsBuilder {
  @Nullable
  String getContentEntryPath();

  void setContentEntryPath(String moduleRootPath);

  List<Pair<String,String>> getSourcePaths();

  void setSourcePaths(List<Pair<String,String>> sourcePaths);

  void addSourcePath(Pair<String,String> sourcePathInfo);
}
