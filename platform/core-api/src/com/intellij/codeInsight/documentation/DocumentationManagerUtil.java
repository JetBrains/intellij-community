// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.psi.PsiElement;

public final class DocumentationManagerUtil {

  private DocumentationManagerUtil() { }

  /**
   * Appends a hyperlink to the specified element to the specified string buffer.
   *
   * @param buffer    the target buffer.
   * @param refText   the hyperlink reference text
   * @param label     the label for the hyperlink
   * @param plainLink if false, the label of the link is wrapped in the &lt;code&gt; tag.
   */
  public static void createHyperlink(StringBuilder buffer, String refText, String label, boolean plainLink) {
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

  /**
   * @deprecated use {@link #createHyperlink(StringBuilder, String, String, boolean)}
   */
  @Deprecated
  public static void createHyperlink(StringBuilder buffer,
                                     String refText,
                                     String label,
                                     boolean plainLink,
                                     @SuppressWarnings("unused") boolean isRendered) {
    createHyperlink(buffer, refText, label, plainLink);
  }

  /**
   * @deprecated use {@link #createHyperlink(StringBuilder, String, String, boolean)}
   */
  @Deprecated
  public static void createHyperlink(
    StringBuilder buffer,
    PsiElement refElement,
    String refText,
    String label,
    boolean plainLink
  ) {
    createHyperlink(buffer, refText, label, plainLink);
  }

  /**
   * @deprecated use {@link #createHyperlink(StringBuilder, String, String, boolean)}
   */
  @Deprecated
  public static void createHyperlink(
    StringBuilder buffer,
    @SuppressWarnings("unused") PsiElement refElement,
    String refText,
    String label,
    boolean plainLink,
    @SuppressWarnings("unused") boolean isRendered
  ) {
    createHyperlink(buffer, refText, label, plainLink);
  }
}
