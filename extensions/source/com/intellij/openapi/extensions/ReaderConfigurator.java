/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

import com.thoughtworks.xstream.XStream;

/**
 * @author AKireyev
 */
public interface ReaderConfigurator {
  void configureReader(XStream xstream);
}
