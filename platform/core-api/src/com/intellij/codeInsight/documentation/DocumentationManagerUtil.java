// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;

public class DocumentationManagerUtil {
  public static DocumentationManagerUtil getInstance() {
    return ApplicationManager.getApplication().getService(DocumentationManagerUtil.class);
  }

  @SuppressWarnings({"HardCodedStringLiteral", "UnusedParameters"})
  protected void createHyperlinkImpl(
    StringBuilder buffer,
    PsiElement refElement,
    String refText,
    String label,
    boolean plainLink,
    boolean isRendered
  ) {
    buffer.append("<a href=\"");
    buffer.append(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL); // :-)
    buffer.append(refText);
    buffer.append("\">");
    if (!plainLink) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        buffer.append(isRendered ? "<code style='font-size:96%;'>" : "<code>");
      }
      else {
        buffer.append("<code style='font-size:");
        buffer.append(getMonospaceFontSizeCorrection(isRendered));
        buffer.append("%;'>");
      }
    }
    buffer.append(label);
    if (!plainLink) {
      buffer.append("</code>");
    }
    buffer.append("</a>");
  }

  private static int getMonospaceFontSizeCorrection(boolean isRendered) {
    if (isRendered) {
      return SystemInfo.isWin10OrNewer && !ApplicationManager.getApplication().isUnitTestMode() ? 90 : 96;
    }
    else {
      return SystemInfo.isWin10OrNewer && !ApplicationManager.getApplication().isUnitTestMode() ? 90 : 100;
    }
  }

  public static void createHyperlink(StringBuilder buffer, String refText, String label, boolean plainLink) {
    getInstance().createHyperlinkImpl(buffer, null, refText, label, plainLink, false);
  }

  public static void createHyperlink(StringBuilder buffer, String refText, String label, boolean plainLink, boolean isRendered) {
    getInstance().createHyperlinkImpl(buffer, null, refText, label, plainLink, isRendered);
  }

  public static void createHyperlink(
    StringBuilder buffer,
    PsiElement refElement,
    String refText,
    String label,
    boolean plainLink
  ) {
    getInstance().createHyperlinkImpl(buffer, refElement, refText, label, plainLink, false);
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
  public static void createHyperlink(
    StringBuilder buffer,
    PsiElement refElement,
    String refText,
    String label,
    boolean plainLink,
    boolean isRendered
  ) {
    getInstance().createHyperlinkImpl(buffer, refElement, refText, label, plainLink, isRendered);
  }
}
