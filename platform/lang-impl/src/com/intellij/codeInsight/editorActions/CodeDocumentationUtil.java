package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.project.Project;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

/**
 * @author yole
 */
public class CodeDocumentationUtil {
  private CodeDocumentationUtil() {
  }

  public static String createDocCommentLine(String lineData, Project project, CodeDocumentationAwareCommenter commenter) {
    if (!CodeStyleSettingsManager.getSettings(project).JD_LEADING_ASTERISKS_ARE_ENABLED) {
      return " " + lineData + " ";
    }
    else {
      if (lineData.length() == 0) {
        return commenter.getDocumentationCommentLinePrefix() + " ";
      }
      else {
        return commenter.getDocumentationCommentLinePrefix() + " " + lineData + " ";
      }

    }
  }
}
