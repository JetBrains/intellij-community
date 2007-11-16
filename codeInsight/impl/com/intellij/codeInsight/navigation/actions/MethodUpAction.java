
package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.navigation.MethodUpHandler;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.xml.XmlFile;

/**
 *
 */
public class MethodUpAction extends BaseCodeInsightAction {
  protected CodeInsightActionHandler getHandler() {
    return new MethodUpHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return checkValidForFile(file);
  }

  static boolean checkValidForFile(final PsiFile file) {
    if (file instanceof PsiJavaFile || file instanceof XmlFile) {
      return true;
    }
    final StructureViewBuilder structureViewBuilder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(file);
    if (structureViewBuilder instanceof TreeBasedStructureViewBuilder) {
      return true;
    }
    return false;
  }
}