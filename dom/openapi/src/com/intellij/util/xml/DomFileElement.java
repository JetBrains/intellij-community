/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface DomFileElement<T extends DomElement> extends DomElement, UserDataHolder, ModificationTracker {
  @NotNull
  XmlFile getFile();

  @NotNull
  XmlFile getOriginalFile();

  @Nullable
  XmlTag getRootTag();

  @NotNull
  T getRootElement();

  @NotNull
  Class<T> getRootElementClass();

  @NotNull
  DomFileDescription<T> getFileDescription();

}
