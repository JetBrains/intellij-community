/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.xml;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;

import java.util.Map;

/**
 * @author Mike
 */
public interface XmlTag extends XmlElement, PsiNamedElement, PsiMetaOwner, XmlTagChild {
  XmlTag[] EMPTY = new XmlTag[0];

  String getName();
  String getNamespace();
  String getLocalName();

  XmlElementDescriptor getDescriptor();

  XmlAttribute[] getAttributes();
  XmlAttribute getAttribute(String name, String namespace);

  String getAttributeValue(String name);
  String getAttributeValue(String name, String namespace);

  XmlAttribute setAttribute(String name, String namespace, String value) throws IncorrectOperationException;
  XmlAttribute setAttribute(String name, String value) throws IncorrectOperationException;

  XmlTag createChildTag(String localName, String namespace, String bodyText, boolean enforceNamespacesDeep);

  XmlTag[] getSubTags();
  XmlTag[] findSubTags(String qname);
  XmlTag[] findSubTags(String localName, String namespace);
  XmlTag findFirstSubTag(String qname);

  String getNamespacePrefix();
  String getNamespaceByPrefix(String prefix);
  String getPrefixByNamespace(String namespace);
  String[] knownNamespaces();

  boolean hasNamespaceDeclarations();
  Map<String, String> getLocalNamespaceDeclarations();

  XmlTagValue getValue();

  XmlNSDescriptor getNSDescriptor(String namespace, boolean strict);

  boolean isEmpty();
}
