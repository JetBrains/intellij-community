/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiElement;

public class DocumentationManagerUtil {
  public static DocumentationManagerUtil getInstance() {
    return ServiceManager.getService(DocumentationManagerUtil.class);
  }

  @SuppressWarnings({"HardCodedStringLiteral", "MethodMayBeStatic", "UnusedParameters"})
  protected void createHyperlinkImpl(StringBuilder buffer, PsiElement refElement, String refText, String label, boolean plainLink) {
    buffer.append("<a href=\"");
    buffer.append(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL); // :-)
    buffer.append(refText);
    buffer.append("\">");
    if (!plainLink) {
      buffer.append("<code>");
    }
    buffer.append(label);
    if (!plainLink) {
      buffer.append("</code>");
    }
    buffer.append("</a>");
  }

  public static void createHyperlink(StringBuilder buffer, String refText, String label, boolean plainLink) {
    getInstance().createHyperlinkImpl(buffer, null, refText, label, plainLink);
  }

  /**
   * Appends a hyperlink to the specified element to the specified string buffer.
   *
   * @param buffer     the target buffer.
   * @param refElement the element to which the link is generated.
   * @param refText    the hyperlink reference text
   * @param label      the label for the hyperlink
   * @param plainLink  if false, the label of the link is wrapped in the &lt;code&gt; tag.
   */
  public static void createHyperlink(StringBuilder buffer, PsiElement refElement, String refText, String label, boolean plainLink) {
    getInstance().createHyperlinkImpl(buffer, refElement, refText, label, plainLink);
  }
}
