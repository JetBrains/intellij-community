package com.intellij.util.xml.ui.actions.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomReflectionUtil;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;

import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
public abstract class DefaultGenerateElementProvider extends GenerateDomElementProvider {
  private Class<? extends DomElement> myChildElementClass;

  public DefaultGenerateElementProvider(final String name, Class<? extends DomElement> childElementClass) {
      super(name);

    myChildElementClass = childElementClass;
  }


  public DomElement generate(final Project project, final Editor editor, final PsiFile file) {
    return generate(getParentDomElement(project, editor, file));
  }

  protected abstract DomElement getParentDomElement(final Project project, final Editor editor, final PsiFile file);

  public DomElement generate(final DomElement parent) {
    final List<? extends DomCollectionChildDescription> list = parent.getGenericInfo().getCollectionChildrenDescriptions();

    for (DomCollectionChildDescription childDescription : list) {
      if (DomReflectionUtil.getRawType(childDescription.getType()).isAssignableFrom(myChildElementClass)) {
        return childDescription.addValue(parent, myChildElementClass);
      }
    }

    return null;
  }
}
