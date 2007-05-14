package com.intellij.codeInsight.daemon.impl.analysis.encoding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlChildRole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
public class XmlEncodingReferenceProvider implements PsiReferenceProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.encoding.XmlEncodingReferenceProvider");
  @NonNls private static final String CHARSET_PREFIX = "charset=";

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    LOG.assertTrue(element instanceof XmlAttributeValue);
    XmlAttributeValue value = (XmlAttributeValue)element;

    return new PsiReference[]{new XmlEncodingReference(value, value.getValue(), xmlAttributeValueRange(value))};
  }

  protected static TextRange xmlAttributeValueRange(final XmlAttributeValue xmlAttributeValue) {
    ASTNode valueNode = XmlChildRole.ATTRIBUTE_VALUE_VALUE_FINDER.findChild(xmlAttributeValue.getNode());
    PsiElement toHighlight = valueNode == null ? xmlAttributeValue : valueNode.getPsi();
    TextRange childRange = toHighlight.getTextRange();
    TextRange range = xmlAttributeValue.getTextRange();
    return childRange.shiftRight(-range.getStartOffset());
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {

  }

  public static PsiReference[] extractFromContentAttribute(final XmlAttributeValue value) {
    String text = value.getValue();
    int start = text.indexOf(CHARSET_PREFIX);
    if (start != -1) {
      start += CHARSET_PREFIX.length();
      int end = text.indexOf(';', start);
      if (end == -1) end = text.length();
      String charsetName = text.substring(start, end);
      TextRange textRange = new TextRange(start, end).shiftRight(xmlAttributeValueRange(value).getStartOffset());
      return new PsiReference[]{new XmlEncodingReference(value, charsetName, textRange)};
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
