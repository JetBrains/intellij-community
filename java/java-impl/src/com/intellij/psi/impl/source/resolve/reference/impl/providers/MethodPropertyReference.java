/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.*;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class MethodPropertyReference extends BasicAttributeValueReference {
  private final boolean myReadable;

  public MethodPropertyReference(final PsiElement element, boolean readable) {
    super(element);
    myReadable = readable;
  }

  protected PsiClass resolveClass() {
    final PsiElement classReferencesElement = getClassReferencesElement();

    if (classReferencesElement != null) {
      final PsiReference[] references = classReferencesElement.getReferences();

      if (references.length > 0) {
        final PsiElement psiElement = references[references.length - 1].resolve();

        if (psiElement instanceof XmlAttributeValue) {
          final XmlTag beanTag = (XmlTag)psiElement.getParent().getParent();
          XmlAttribute attribute = beanTag.getAttribute("class", null);
          if (attribute == null) attribute = beanTag.getAttribute("type", null);

          if (attribute != null) {
            final PsiReference[] classReferences = attribute.getValueElement().getReferences();

            if (classReferences.length > 0) {
              final PsiElement classElement = classReferences[classReferences.length - 1].resolve();

              if (classElement instanceof PsiClass) return (PsiClass)classElement;
            }
          }
        } else if (psiElement instanceof PsiClass) {
          return (PsiClass)psiElement;
        } else if (psiElement instanceof PsiVariable) {
          final PsiType type=((PsiVariable)psiElement).getType();
          if (type instanceof PsiClassType) {
            return ((PsiClassType)type).resolve();
          }
        }
      }
    }

    return null;
  }

  protected PsiElement getClassReferencesElement() {
    final XmlTag tag = (XmlTag)myElement.getParent().getParent();
    final XmlAttribute name = tag.getAttribute("name",null);
    if (name != null) { return name.getValueElement(); }
    return null;
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    return JspSpiUtil.resolveMethodPropertyReference(this, resolveClass(), myReadable);
  }


  @Override
  public PsiElement handleElementRename(String _newElementName) throws IncorrectOperationException {
    String newElementName = PropertyUtilBase.getPropertyName(_newElementName);
    if (newElementName == null) newElementName = _newElementName;

    return super.handleElementRename(newElementName);
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    return JspSpiUtil.getMethodPropertyReferenceVariants(this, resolveClass(), myReadable);
  }

  @Override
  public boolean isSoft() {
    return true;
  }
}
