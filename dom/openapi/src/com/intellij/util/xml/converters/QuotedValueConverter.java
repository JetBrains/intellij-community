/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Sergey.Vasiliev
 * Date: Nov 13, 2006
 * Time: 4:37:22 PM
 */
package com.intellij.util.xml.converters;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.xml.*;
import com.intellij.xml.util.XmlTagTextUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public abstract class QuotedValueConverter<T> extends ResolvingConverter<T> implements CustomReferenceConverter<T> {

  public static final char[] QUOTE_SIGNS = new char[] {'\'', '\"', '`'};

  protected char[] getQuoteSigns() {
    return QUOTE_SIGNS;
  }

  protected char getQuoteSign(final T t, final ConvertContext context) {
    return 0;
  }

  @Nullable
  protected abstract T convertString(final @Nullable String string, final ConvertContext context);

  @Nullable
  protected abstract String convertValue(@Nullable final T t, final ConvertContext context);

  protected abstract Object[] getReferenceVariants(final ConvertContext context, GenericDomValue<T> genericDomValue);

  @Nullable
  protected abstract PsiElement resolveReference(@Nullable final T t, final ConvertContext context);

  protected abstract String getUnresolvedMessage(String value);

  @NotNull
  public Collection<? extends T> getVariants(final ConvertContext context) {
    return Collections.emptyList();
  }

  public T fromString(final String str, final ConvertContext context) {
    return convertString(unquote(str, getQuoteSigns()), context);
  }

  public String toString(final T ts, final ConvertContext context) {
    final char delimiter = getQuoteSign(ts, context);
    final String s = convertValue(ts, context);
    return delimiter > 0? delimiter + s+ delimiter : s;
  }

  @NotNull
  public PsiReference[] createReferences(final GenericDomValue<T> genericDomValue,
                                         final PsiElement element,
                                         final ConvertContext context) {
    final String originalValue = genericDomValue.getStringValue();
    if (originalValue == null) return PsiReference.EMPTY_ARRAY;
    final String unquotedValue = unquote(originalValue, getQuoteSigns());
    int startOffset = originalValue == unquotedValue? 0 : XmlTagTextUtil.escapeString(originalValue.substring(0, 1), false).length();
    int endOffset = originalValue == unquotedValue || quotationIsNotClosed(originalValue)? 0 : startOffset;
    return new PsiReference[]{createPsiReference(element, startOffset+1, element.getTextLength() - 1 - endOffset, context, genericDomValue, startOffset != endOffset)};
  }

  @Nullable
  public static String unquote(final String str) {
    return unquote(str, QUOTE_SIGNS);
  }

  @Nullable
  public static String unquote(final String str, final char[] quoteSigns) {
    if (str != null && str.length() > 2) {
      final char c = str.charAt(0);
      for (char quote : quoteSigns) {
        if (quote == c) {
          return str.substring(1, c == str.charAt(str.length() - 1)? str.length() - 1 : str.length());
        }
      }
    }
    return str;
  }

  public static boolean quotationIsNotClosed(final String str) {
    return StringUtil.isNotEmpty(str) && str.charAt(0) != str.charAt(str.length()-1);
  }

  @NotNull
  protected PsiReference createPsiReference(final PsiElement element,
                                            int start, int end,
                                            final ConvertContext context,
                                            final GenericDomValue<T> genericDomValue, final boolean badQuotation) {

    return new MyPsiReference(element, new TextRange(start, end), context, genericDomValue, badQuotation);
  }

  protected class MyPsiReference extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider {
    protected final ConvertContext myContext;
    protected final GenericDomValue<T> myGenericDomValue;
    private final boolean myBadQuotation;

    public MyPsiReference(final PsiElement element, final TextRange range, final ConvertContext context, final GenericDomValue<T> genericDomValue,
                          final boolean badQuotation) {
      super(element, range);
      myContext = context;
      myGenericDomValue = genericDomValue;
      myBadQuotation = badQuotation;
    }

    @Nullable
    public PsiElement resolve() {
      if (myBadQuotation) return null;
      final String value = getValue();
      return resolveReference(convertString(value, myContext), myContext);
    }

    public Object[] getVariants() {
      return getReferenceVariants(myContext, myGenericDomValue);
    }

    public String getUnresolvedMessagePattern() {
      return myBadQuotation? DomBundle.message("message.invalid.value.quotation") : getUnresolvedMessage(getValue());
    }
  }
}
