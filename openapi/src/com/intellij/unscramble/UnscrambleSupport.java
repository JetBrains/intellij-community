/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 6, 2004
 * Time: 5:12:43 PM
 */
package com.intellij.unscramble;

import com.intellij.openapi.project.Project;

public interface UnscrambleSupport {
  String unscramble(Project project, String text, String logName);
  String getPresentableName();
}