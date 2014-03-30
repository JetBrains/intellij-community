/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.extensions;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jdom.Element;

/**
 * @author Alexander Kireyev
 */
public class SortingException extends RuntimeException {
  private final Element[] myConflictingElements;

  public SortingException(String message, Element... conflictingElements) {
    super(message + ": " + StringUtil.join(conflictingElements, new Function<Element, String>() {
      @Override
      public String fun(Element element) {
        return element.getAttributeValue("id") + "(" + element.getAttributeValue("order") + ")";
      }
    }, "; "));
    myConflictingElements = conflictingElements;
  }

  public Element[] getConflictingElements() {
    return myConflictingElements;
  }
}
