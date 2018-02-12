package com.intellij.codeInsight.hint;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class ShowParameterInfoContextFactoryImpl implements ShowParameterInfoContextFactory {
  @NotNull
  @Override
  public ShowParameterInfoContext createShowParameterInfoContext(Project project,
                                                                 Editor editor,
                                                                 PsiFile file,
                                                                 int lbraceOffset,
                                                                 int offset,
                                                                 boolean requestFocus,
                                                                 boolean singleParameterHint) {
    return new ShowParameterInfoContext(editor, project, file, offset, lbraceOffset, requestFocus, singleParameterHint);
  }
}
