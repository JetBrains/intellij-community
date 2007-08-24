/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import com.intellij.util.xml.XmlName;
import com.intellij.psi.xml.XmlElement;

/**
 * @author peter
 */
public interface EvaluatedXmlName {

  XmlName getXmlName();

  EvaluatedXmlName evaluateChildName(@NotNull XmlName name);

  boolean isNamespaceAllowed(DomInvocationHandler handler, String namespace);

  @NotNull @NonNls
  String getNamespace(@NotNull XmlElement parentElement);
}
