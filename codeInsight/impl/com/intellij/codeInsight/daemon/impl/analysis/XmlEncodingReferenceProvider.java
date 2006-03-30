package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class XmlEncodingReferenceProvider implements PsiReferenceProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.XmlEncodingReferenceProvider");

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    LOG.assertTrue(element instanceof XmlAttributeValue);
    XmlAttributeValue value = (XmlAttributeValue)element;

    return new PsiReference[]{new XmlEncodingReference(value, value.getValue())};
  }

  private static class XmlEncodingReference implements PsiReference, EmptyResolveMessageProvider {
    private final XmlAttributeValue myValue;
    private final String myCharsetName;

    public XmlEncodingReference(XmlAttributeValue value, final String charsetName) {
      myValue = value;
      myCharsetName = charsetName;
    }

    public PsiElement getElement() {
      return myValue;
    }

    public TextRange getRangeInElement() {
      ASTNode valueNode = XmlChildRole.ATTRIBUTE_VALUE_VALUE_FINDER.findChild(myValue.getNode());
      PsiElement toHighlight = valueNode == null ? myValue : valueNode.getPsi();
      TextRange childRange = toHighlight.getTextRange();
      TextRange range = myValue.getTextRange();
      return new TextRange(childRange.getStartOffset()-range.getStartOffset(), childRange.getEndOffset()-range.getStartOffset());
    }

    @Nullable
    public PsiElement resolve() {
      Charset charset;
      try {
        charset = Charset.forName(myCharsetName);
      }
      catch (Exception e) {
        return null;
      }
      return myValue;
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
}
