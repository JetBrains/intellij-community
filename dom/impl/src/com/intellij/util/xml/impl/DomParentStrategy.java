/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface DomParentStrategy {
  @Nullable DomInvocationHandler getParentHandler();

  @Nullable
  XmlElement getXmlElement();

  @NotNull DomParentStrategy refreshStrategy(final DomInvocationHandler handler);

  @NotNull DomParentStrategy setXmlElement(@NotNull XmlElement element);

  @NotNull DomParentStrategy clearXmlElement();

  boolean isValid();
}
