/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;

import java.util.Map;

/**
 * @author Mike
 */
public interface XmlFile extends PsiFile, XmlElement {
  Key<Map<String,String>> ANT_FILE_PROPERTIES = Key.create("ANT_FILE_PROPERTIES");
  Key ANT_BUILD_FILE = Key.create("ANT_BUILD_FILE");

  XmlDocument getDocument();
}
