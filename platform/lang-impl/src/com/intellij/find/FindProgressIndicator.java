package com.intellij.find;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;

/**
 * @author ven
 */
public class FindProgressIndicator extends BackgroundableProcessIndicator {
  public FindProgressIndicator(Project project, String scopeString) {
    super(project,
         FindBundle.message("find.progress.searching.message", scopeString),
         new SearchInBackgroundOption(),
         FindBundle.message("find.progress.stop.title"),
         FindBundle.message("find.progress.stop.background.button"), true);
  }
}
