package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"unchecked", "CastToIncompatibleInterface", "InstanceofIncompatibleInterface"})
public abstract class BasePsiNode <T extends PsiElement> extends AbstractPsiBasedNode<T> {
  protected BasePsiNode(Project project, T value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Nullable
  protected PsiElement extractPsiFromValue() {
    return getValue();
  }
}
