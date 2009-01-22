/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.deployment;

import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
*/
interface LibraryInfo {
  String getName();

  @NotNull
  List<String> getUrls();

  String getLevel();

  void readExternal(Element element) throws InvalidDataException;
}
