// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView;

/**
 * @author nik
 */
public interface ProjectViewSettings extends ViewSettings {
  boolean isShowExcludedFiles();

  /**
   * If {@code true} then {@link com.intellij.ide.projectView.impl.NestingTreeStructureProvider} will modify the tree presentation
   * according to the rules managed by {@link com.intellij.ide.projectView.impl.ProjectViewFileNestingService}: some peer files will be
   * shown as nested, for example generated {@code foo.js} and {@code foo.js.map} file nodes will be shown as children of the
   * original {@code foo.ts} node in the Project View.
   */
  default boolean isUseFileNestingRules() {return true;}

  class Immutable extends ViewSettings.Immutable implements ProjectViewSettings {
    private final boolean myShowExcludedFiles;
    private final boolean myUseFileNestingRules;

    public Immutable(ProjectViewSettings settings) {
      super(settings);
      myShowExcludedFiles = settings != null && settings.isShowExcludedFiles();
      myUseFileNestingRules = settings == null || settings.isUseFileNestingRules();
    }

    @Override
    public boolean isShowExcludedFiles() {
      return myShowExcludedFiles;
    }

    @Override
    public boolean isUseFileNestingRules() {
      return myUseFileNestingRules;
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) return true;
      if (!super.equals(object)) return false;
      ProjectViewSettings settings = (ProjectViewSettings)object;
      return settings.isShowExcludedFiles() == isShowExcludedFiles() &&
             settings.isUseFileNestingRules() == isUseFileNestingRules();
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + Boolean.hashCode(isShowExcludedFiles());
      result = 31 * result + Boolean.hashCode(isUseFileNestingRules());
      return result;
    }
  }
}
