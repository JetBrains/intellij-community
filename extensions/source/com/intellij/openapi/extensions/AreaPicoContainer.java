/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

import org.picocontainer.MutablePicoContainer;
import org.picocontainer.defaults.ComponentAdapterFactory;

/**
 * @author Alexander Kireyev
 */
public interface AreaPicoContainer extends MutablePicoContainer {
  void setComponentAdapterFactory(ComponentAdapterFactory factory);
}
