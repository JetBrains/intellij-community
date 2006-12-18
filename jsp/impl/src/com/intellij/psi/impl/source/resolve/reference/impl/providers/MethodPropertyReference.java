/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.impl.source.jsp.el.impl.ELResolveUtil;
import com.intellij.psi.jsp.JspImplicitVariable;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.lang.properties.psi.Property;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.HashMap;

/**
 * @author peter
*/
public class MethodPropertyReference extends BasicAttributeValueReference {
  private boolean myReadable;

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
        } else if (psiElement instanceof JspImplicitVariable) {
          final PsiType type=((JspImplicitVariable)psiElement).getType();
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
    final XmlAttribute name = tag.getAttribute(JspReferencesProvider.NAME_ATTR_NAME,null);
    if (name != null) { return name.getValueElement(); }
    return null;
  }

  @Nullable
  public PsiElement resolve() {
    final String name = getCanonicalText();
    final PsiElement result[] = new PsiElement[1];

    final ELResolveUtil.ELElementProcessor processor = new ELResolveUtil.ELElementProcessor() {
      private PsiSubstitutor mySubstitutor;

      public boolean processNSPrefix(String prefix) { return false; }
      public boolean processVariable(PsiVariable variable) { return false; }
      public boolean processMethod(PsiMethod method) {
        if (name.equals(PropertyUtil.getPropertyName(method))) {
          result[0] = method;
          method.putUserData(ELResolveUtil.SUBSTITUTOR,mySubstitutor);
          return false;
        }
        return true;
      }

      public boolean processProperty(Property property) {
        return false;
      }

      public void setSubstitutor(final PsiSubstitutor substitutor) {
        mySubstitutor = substitutor;
      }

      @Nullable
      public String getNameHint() {
        return null;
      }
    };

    processProperties(processor);

    return result[0];
  }

  private void processProperties(final ELResolveUtil.ELElementProcessor processor) {
    final PsiClass psiClass = resolveClass();

    if (psiClass != null) {
      ELResolveUtil.iterateClassProperties(
        psiClass,
        processor,
        myReadable
      );
    }
  }

  public PsiElement handleElementRename(String _newElementName) throws IncorrectOperationException {
    String newElementName = PropertyUtil.getPropertyName(_newElementName);
    if (newElementName == null) newElementName = _newElementName;

    return super.handleElementRename(newElementName);
  }

  public Object[] getVariants() {
    final Map<String,JspReferencesProvider.MyPropertyResultLookupValue> properties = new HashMap<String,JspReferencesProvider.MyPropertyResultLookupValue>(1);

    processProperties(
      new ELResolveUtil.ELElementProcessor() {
        public boolean processNSPrefix(String prefix) { return false; }
        public boolean processVariable(PsiVariable variable) { return false; }

        public boolean processMethod(PsiMethod method) {
          final JspReferencesProvider.MyPropertyResultLookupValue myPropertyResultLookupValue =
            new JspReferencesProvider.MyPropertyResultLookupValue(method, myReadable);

          if (!properties.containsKey(myPropertyResultLookupValue.getPresentation())) {
            properties.put(
              myPropertyResultLookupValue.getPresentation(),
              myPropertyResultLookupValue
            );
          }
          return true;
        }

        public boolean processProperty(Property property) {
          return false;
        }


        @Nullable
        public String getNameHint() {
          return null;
        }

        public void setSubstitutor(final PsiSubstitutor mySubstitutor) {}
      }
    );

    return properties.values().toArray(new Object[properties.size()]);
  }

  public boolean isSoft() {
    return true;
  }
}
