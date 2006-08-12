package com.intellij.codeInsight.completion;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.TextStartFilter;
import com.intellij.psi.filters.getters.HtmlAttributeValueGetter;
import com.intellij.psi.filters.getters.XmlAttributeValueGetter;
import com.intellij.psi.filters.position.TokenTypeFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Oct 13, 2004
 * Time: 6:50:33 PM
 * To change this template use File | Settings | File Templates.
 */
@SuppressWarnings({"RefusedBequest"})
public class HtmlCompletionData extends XmlCompletionData {
  private static CompletionData ourStyleCompletionData;
  private boolean myCaseInsensitive;
  private static CompletionData ourScriptCompletionData;
  private static final @NonNls String JAVASCRIPT_LANGUAGE_ID = "JavaScript";
  private static final @NonNls String STYLE_TAG = "style";
  private static final @NonNls String SCRIPT_TAG = "script";

  public HtmlCompletionData() {
    this(true);
  }

  protected HtmlCompletionData(boolean _caseInsensitive) {
    myCaseInsensitive = _caseInsensitive;
  }

  protected ElementFilter createXmlEntityCompletionFilter() {
    if (isCaseInsensitive()) {
      return new AndFilter(
        new TokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS),
        new TextStartFilter("&")
      );
    }
    
    return super.createXmlEntityCompletionFilter();
  }

  private boolean equalNames(String str,String str2) {
    if (!myCaseInsensitive) return str.equals(str2);
    return str.equalsIgnoreCase(str2);
  }

  protected boolean isCaseInsensitive() {
    return true;
  }

  protected void setCaseInsensitive(final boolean caseInsensitive) {
    myCaseInsensitive = caseInsensitive;
  }

  protected XmlAttributeValueGetter getAttributeValueGetter() {
    return new HtmlAttributeValueGetter(!isCaseInsensitive());
  }

  protected ElementFilter createTagCompletionFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        String name = ((XmlTag)context).getName();
        if (name == null) return true;

        if (equalNames(name, STYLE_TAG) ||
            equalNames(name,SCRIPT_TAG)) {
          return false;
        }

        if ( isStyleAttributeContext((PsiElement)element) ) return false;
        return true;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }

  protected ElementFilter createAttributeCompletionFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        if (isStyleAttributeContext(context)) return false;
        return true;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }

  protected ElementFilter createAttributeValueCompletionFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        if (isStyleAttributeContext(context)) return false;
        if ( isScriptContext((PsiElement)element) ) return false;
        return true;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }

  private boolean isScriptContext(PsiElement element) {
    final Language language = element.getLanguage();

    return language.getID().equals(JAVASCRIPT_LANGUAGE_ID);
  }

  private boolean isScriptTag(XmlTag tag) {
    if (tag!=null) {
      String tagName = tag.getName();
      if (tagName == null) return false;
      if (myCaseInsensitive) return tagName.equalsIgnoreCase(SCRIPT_TAG);

      return tagName.equals(SCRIPT_TAG);
    }

    return false;
  }

  private boolean isStyleTag(XmlTag tag) {
    if (tag!=null) {
      String tagName = tag.getName();
      if (tagName == null) return false;
      if (myCaseInsensitive) return tagName.equalsIgnoreCase(STYLE_TAG);

      return tagName.equals(STYLE_TAG);
    }

    return false;
  }

  public CompletionVariant[] findVariants(PsiElement position, CompletionContext context) {
    CompletionVariant[] variants = super.findVariants(position, context);

    if (ourStyleCompletionData!=null && isStyleContext(position)) {
      final CompletionVariant[] styleVariants = ourStyleCompletionData.findVariants(position, context);

      variants = ArrayUtil.mergeArrays(variants,styleVariants, CompletionVariant.class);
    }

    if (ourScriptCompletionData!=null && isScriptContext(position)) {
      final CompletionVariant[] scriptVariants = ourScriptCompletionData.findVariants(position, context);

      variants = ArrayUtil.mergeArrays(variants,scriptVariants, CompletionVariant.class);
    }

    return variants;
  }

  private boolean isStyleAttributeContext(PsiElement position) {
    XmlAttribute parentOfType = PsiTreeUtil.getParentOfType(position, XmlAttribute.class, false);

    if (parentOfType != null) {
      String name = parentOfType.getName();
      if (name != null) {
        if (myCaseInsensitive) return STYLE_TAG.equalsIgnoreCase(name);
        return STYLE_TAG.equals(name); //name.endsWith("style");
      }
    }

    return false;
  }
  private boolean isStyleContext(PsiElement position) {
    if (isStyleAttributeContext(position)) return true;

    return isStyleTag(PsiTreeUtil.getParentOfType(position, XmlTag.class, false));
  }

  public void addKeywordVariants(Set<CompletionVariant> set, CompletionContext context, PsiElement position) {
    super.addKeywordVariants(set, context, position);

    if (ourStyleCompletionData!=null && isStyleContext(position)) {
      ourStyleCompletionData.addKeywordVariants(set, context, position);
    } else if (ourScriptCompletionData!=null && isScriptContext(position)) {
      ourScriptCompletionData.addKeywordVariants(set, context, position);
    }
  }

  public static void setStyleCompletionData(CompletionData cssCompletionData) {
    ourStyleCompletionData = cssCompletionData;
  }

  public void registerVariant(CompletionVariant variant) {
    super.registerVariant(variant);
    if (isCaseInsensitive()) variant.setCaseInsensitive(true);
  }

  public String findPrefix(PsiElement insertedElement, int offset) {
    XmlTag tag = PsiTreeUtil.getParentOfType(insertedElement, XmlTag.class, false);
    String prefix = null;

    if (isScriptTag(tag) &&
        ourScriptCompletionData != null &&
        !(insertedElement.getParent() instanceof XmlAttributeValue)) {
      prefix = ourScriptCompletionData.findPrefix(insertedElement, offset);
    } else if (isStyleTag(tag) && ourStyleCompletionData!=null) {
      prefix = ourStyleCompletionData.findPrefix(insertedElement, offset);
    }

    if (prefix == null) {
      prefix = super.findPrefix(insertedElement, offset);
      
      if (insertedElement instanceof XmlToken &&
          ((XmlToken)insertedElement).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS &&
          prefix != null &&
          prefix.startsWith("&")
         ) {
        prefix = prefix.substring(1);
      }
    }

    return prefix;
  }

  public static void setScriptCompletionData(CompletionData scriptCompletionData) {
    ourScriptCompletionData = scriptCompletionData;
  }
}
