package com.intellij.codeInsight.daemon.impl.analysis.encoding;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
*/
public class XmlEncodingReference implements PsiReference, EmptyResolveMessageProvider, Comparable<XmlEncodingReference> {
  private final XmlAttributeValue myValue;

  private final String myCharsetName;
  private final TextRange myRangeInElement;
  private final int myPriority;

  public XmlEncodingReference(XmlAttributeValue value, final String charsetName, final TextRange rangeInElement, int priority) {
    myValue = value;
    myCharsetName = charsetName;
    myRangeInElement = rangeInElement;
    myPriority = priority;
  }

  public PsiElement getElement() {
    return myValue;
  }

  public TextRange getRangeInElement() {
    return myRangeInElement;
  }

  @Nullable
  public PsiElement resolve() {
    return CharsetToolkit.forName(myCharsetName) == null ? null : myValue;
    //if (ApplicationManager.getApplication().isUnitTestMode()) return myValue; // tests do not have full JDK
    //String fqn = charset.getClass().getName();
    //return myValue.getManager().findClass(fqn, GlobalSearchScope.allScope(myValue.getProject()));
  }

  public String getUnresolvedMessagePattern() {
    return XmlErrorMessages.message("unknown.encoding.0");
  }

  public String getCanonicalText() {
    return myCharsetName;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return null;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    return false;
  }

  public Object[] getVariants() {
    Charset[] charsets = CharsetToolkit.getAvailableCharsets();
    List<LookupItem> suggestions = new ArrayList<LookupItem>(charsets.length);
    for (Charset charset : charsets) {
      String name = charset.name();
      LookupItem item = LookupItemUtil.objectToLookupItem(name);
      item.setAttribute(LookupItem.CASE_INSENSITIVE, true);
      suggestions.add(item);
    }
    return suggestions.toArray(new LookupItem[suggestions.size()]);
  }

  public boolean isSoft() {
    return false;
  }

  public int compareTo(XmlEncodingReference ref) {
    return myPriority - ref.myPriority;
  }
}
