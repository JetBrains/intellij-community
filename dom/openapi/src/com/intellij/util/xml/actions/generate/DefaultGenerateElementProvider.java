/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.xml.actions.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementProvider;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
public abstract class DefaultGenerateElementProvider<T extends DomElement> extends GenerateDomElementProvider<T> {
  private Class<? extends DomElement> myChildElementClass;

  public DefaultGenerateElementProvider(final String name, Class<T> childElementClass) {
    super(name);

    myChildElementClass = childElementClass;
  }


  @Nullable
  public T generate(final Project project, final Editor editor, final PsiFile file) {
    return generate(getParentDomElement(project, editor, file), editor);
  }

  @Nullable
  protected abstract DomElement getParentDomElement(final Project project, final Editor editor, final PsiFile file);

  @Nullable
  public T generate(@Nullable final DomElement parent, final Editor editor) {
    if (parent != null) {
      final List<? extends DomCollectionChildDescription> list = parent.getGenericInfo().getCollectionChildrenDescriptions();

      for (DomCollectionChildDescription childDescription : list) {
        if (ReflectionUtil.getRawType(childDescription.getType()).isAssignableFrom(myChildElementClass)) {
          int index  = getCollectionIndex(parent, childDescription, editor);

          return index < 0 ? (T)childDescription.addValue(parent, myChildElementClass) : (T)childDescription.addValue(parent, myChildElementClass, index) ;
        }
      }
    }
    return null;
  }

  private static int getCollectionIndex(final DomElement parent, final DomCollectionChildDescription childDescription, final Editor editor) {
    int offset = editor.getCaretModel().getOffset();

    for (int i = 0; i < childDescription.getValues(parent).size(); i++) {
      DomElement element = childDescription.getValues(parent).get(i);
      XmlElement xmlElement = element.getXmlElement();
      if (xmlElement != null && xmlElement.getTextRange().getStartOffset() >= offset) {
          return i;
      }
    }

    return -1;
  }
}
