/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;

import java.util.List;

/**
 * @author peter
 */
public interface AbstractCollectionChildDescription extends AbstractDomChildrenDescription {
  List<XmlTag> getSubTags(DomInvocationHandler handler);
  EvaluatedXmlName createEvaluatedXmlName(DomInvocationHandler parent, XmlTag childTag);
}
