package com.intellij.util.xml.impl;

import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.PsiElement;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.dom.DomElementXmlDescriptor;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DefinesXml;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class DomDescriptorProvider implements XmlElementDescriptorProvider {

  @Nullable
  public XmlElementDescriptor getDescriptor(final XmlTag tag) {
    final DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
    if (domElement != null) {
      final DefinesXml definesXml = domElement.getAnnotation(DefinesXml.class);
      if (definesXml != null) {
        return new DomElementXmlDescriptor(domElement);
      }
      final PsiElement parent = tag.getParent();
      if (parent instanceof XmlTag) {
        final XmlElementDescriptor descriptor = ((XmlTag)parent).getDescriptor();

        if (descriptor != null && descriptor instanceof DomElementXmlDescriptor) {
          return descriptor.getElementDescriptor(tag, (XmlTag)parent);
        }
      }
    }

    return null;
  }
}
