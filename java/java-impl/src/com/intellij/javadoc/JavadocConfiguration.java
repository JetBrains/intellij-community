/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.javadoc;

import com.intellij.psi.PsiKeyword;

/**
 * @author Eugene Zhuravlev
 * @since Apr 24, 2004
 */
public class JavadocConfiguration {
  public String OUTPUT_DIRECTORY;
  public String OPTION_SCOPE = PsiKeyword.PROTECTED;
  public boolean OPTION_HIERARCHY = true;
  public boolean OPTION_NAVIGATOR = true;
  public boolean OPTION_INDEX = true;
  public boolean OPTION_SEPARATE_INDEX = true;
  public boolean OPTION_DOCUMENT_TAG_USE;
  public boolean OPTION_DOCUMENT_TAG_AUTHOR;
  public boolean OPTION_DOCUMENT_TAG_VERSION;
  public boolean OPTION_DOCUMENT_TAG_DEPRECATED = true;
  public boolean OPTION_DEPRECATED_LIST = true;
  public String OTHER_OPTIONS;
  public String HEAP_SIZE;
  public String LOCALE;
  public boolean OPEN_IN_BROWSER = true;
  public boolean OPTION_INCLUDE_LIBS;
  public boolean OPTION_LINK_TO_JDK_DOCS;
}