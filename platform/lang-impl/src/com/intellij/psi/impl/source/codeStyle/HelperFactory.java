/*
 * @author max
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;

public class HelperFactory {
  private static Factory INSTANCE = new Factory() {
    public Helper create(final FileType fileType, final Project project) {
      return new Helper(fileType, project);
    }
  };

  private HelperFactory() {
  }

  public static Helper createHelper(FileType fileType, Project project) {
    return INSTANCE.create(fileType, project);
  }

  interface Factory {
    Helper create(FileType fileType, Project project);
  }

  public static void setFactory(Factory factory) {
    INSTANCE = factory;
  }
}