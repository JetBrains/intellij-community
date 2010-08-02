package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaThrowFindUsagesOptions extends JavaFindUsagesOptions {

  public JavaThrowFindUsagesOptions(@NotNull Project project) {
    super(project);
    isSearchForTextOccurrences = false;
  }

}
