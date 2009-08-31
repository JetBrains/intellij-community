/*
 * User: anna
 * Date: 17-Jan-2008
 */
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.ElementPresentationUtil;

public abstract class BasePsiMemberNode<T extends PsiModifierListOwner> extends BasePsiNode<T>{
  protected BasePsiMemberNode(Project project, T value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  protected String calcTooltip() {
    T t = getValue();
    if (t != null && t.isValid()) {
      return ElementPresentationUtil.getDescription(t);
    }
    return super.calcTooltip();
  }

  @Override
  protected boolean isDeprecated() {
    final PsiModifierListOwner element = getValue();
    return element != null && element.isValid() &&
           element instanceof PsiDocCommentOwner &&
           ((PsiDocCommentOwner)element).isDeprecated();
  }
}