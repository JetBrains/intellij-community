package com.intellij.codeInsight.daemon.impl.analysis.encoding;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
*/
class XmlEncodingReference implements PsiReference, EmptyResolveMessageProvider {

  private final XmlAttributeValue myValue;

  private final String myCharsetName;
  private final TextRange myRangeInElement;

  public XmlEncodingReference(XmlAttributeValue value, final String charsetName, final TextRange rangeInElement) {
    myValue = value;
    myCharsetName = charsetName;
    myRangeInElement = rangeInElement;
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

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    return false;
  }

  public Object[] getVariants() {
    Charset[] charsets = CharsetToolkit.getAvailableCharsets();
    List<String> suggestions = new ArrayList<String>(charsets.length);
    for (Charset charset : charsets) {
      suggestions.add(charset.name());
    }
    return suggestions.toArray(new String[suggestions.size()]);
  }

  public boolean isSoft() {
    return false;
  }
}
