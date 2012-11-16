package com.intellij.codeInsight.documentation;

public class DocumentationManagerUtil {
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void createHyperlink(StringBuilder buffer, String refText,String label,boolean plainLink) {
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
}
