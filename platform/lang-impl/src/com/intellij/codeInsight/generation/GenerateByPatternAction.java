package com.intellij.codeInsight.generation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.ArrayUtil;

/**
 * @author Dmitry Avdeev
 */
public class GenerateByPatternAction extends AnAction {

  @Override
  public void update(AnActionEvent e) {
    PatternProvider[] extensions = Extensions.getExtensions(PatternProvider.EXTENSION_POINT_NAME);
    e.getPresentation().setVisible(true);
    for (PatternProvider extension : extensions) {
      if (extension.isAvailable(e.getDataContext())) return;
    }
    e.getPresentation().setVisible(false);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    PatternDescriptor[] patterns = new PatternDescriptor[0];
    PatternProvider[] extensions = Extensions.getExtensions(PatternProvider.EXTENSION_POINT_NAME);
    for (PatternProvider extension : extensions) {
      if (extension.isAvailable(e.getDataContext())) {
        patterns = ArrayUtil.mergeArrays(patterns, extension.getDescriptors(), PatternDescriptor.class);
      }
    }
    GenerateByPatternDialog dialog = new GenerateByPatternDialog(e.getProject(), patterns, e.getDataContext());
    dialog.show();
    if (dialog.isOK()) {
      dialog.getSelectedDescriptor().actionPerformed(e.getDataContext());
    }
  }
}
