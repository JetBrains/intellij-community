package com.intellij.codeInsight.hint;

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface ShowParameterInfoContextFactory {

  @NotNull
  CreateParameterInfoContext createShowParameterInfoContext(Project project,
                                                            Editor editor,
                                                            PsiFile file,
                                                            int lbraceOffset,
                                                            int offset,
                                                            boolean requestFocus,
                                                            boolean singleParameterHint);


  class SERVICE {
    public static ShowParameterInfoContextFactory getInstance() {
      return ServiceManager.getService(ShowParameterInfoContextFactory.class);
    }
  }
}
