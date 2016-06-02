/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.javadoc;

import com.intellij.psi.PsiKeyword;
import com.intellij.util.xmlb.SerializationFilter;

/**
 * @author Eugene Zhuravlev
 * @since Apr 24, 2004
 */
public class JavadocConfiguration {
  public static final SerializationFilter FILTER = (accessor, bean) ->
    !accessor.getName().equals("OPTION_LINK_TO_JDK_DOCS") || ((JavadocConfiguration)bean).OPTION_LINK_TO_JDK_DOCS;

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
  public String OTHER_OPTIONS = "";
  public String HEAP_SIZE;
  public String LOCALE;
  public boolean OPEN_IN_BROWSER = true;
  public boolean OPTION_INCLUDE_LIBS;
  public boolean OPTION_LINK_TO_JDK_DOCS;
}