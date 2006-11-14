/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.model;

import org.jetbrains.annotations.NotNull;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;

import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public interface DomModel<T extends DomElement> {

  @NotNull
  T getMergedModel();

  @NotNull
  Set<XmlFile> getConfigFiles();
}
