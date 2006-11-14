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
public class DomModelImpl<T extends DomElement> {

  protected final T myMergedModel;
  protected final Set<XmlFile> myConfigFiles;

  public DomModelImpl(@NotNull T mergedModel, @NotNull Set<XmlFile> configFiles) {
    myMergedModel = mergedModel;
    myConfigFiles = configFiles;
  }

  @NotNull
  public T getMergedModel() {
    return myMergedModel;
  }

  @NotNull
  public Set<XmlFile> getConfigFiles() {
    return myConfigFiles;
  }
}
