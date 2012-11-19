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

  public static void createHyperlink(StringBuilder buffer, PsiElement refElement, String refText, String label, boolean plainLink) {
    getInstance().createHyperlinkImpl(buffer, refElement, refText, label, plainLink);
  }
}
