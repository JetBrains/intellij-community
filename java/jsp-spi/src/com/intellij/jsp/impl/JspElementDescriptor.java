/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.jsp.impl;

import com.intellij.xml.XmlElementDescriptor;

/**
 * @author Maxim.Mossienko
 */
public interface JspElementDescriptor extends XmlElementDescriptor {

  /**
   * The body of the tag contains nested JSP syntax.
   * @see #getContentType()
   */
  int CONTENT_TYPE_JSP = CONTENT_TYPE_MIXED;

  /**
   * The body accepts only template text, EL Expressions, and JSP action elements.
   * No scripting elements are allowed.
   */
  int CONTENT_TYPE_SCRIPTLESS = 101;

  /**
   * The body of the tag is interpreted by the tag implementation itself,
   * and is most likely in a different "language", e.g embedded SQL statements.
   */
  int CONTENT_TYPE_TAG_DEPENDENT = 102;
}
