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

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.xml.*;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public abstract class DelimitedListConverter<T> extends ResolvingConverter<List<T>> implements CustomReferenceConverter<List<T>> {

  protected final static Object[] EMPTY_ARRAY = new Object[0];
  
  private final String myDelimiters;

  public DelimitedListConverter(@NonNls @NotNull String delimiters) {

    myDelimiters = delimiters;
  }

  @Nullable
  protected abstract T convertString(final @Nullable String string, final ConvertContext context);

  @Nullable
  protected abstract String toString(@Nullable final T t);


  protected abstract Object[] getReferenceVariants(final ConvertContext context, GenericDomValue<List<T>> genericDomValue);

  @Nullable
  protected abstract PsiElement resolveReference(@Nullable final T t, final ConvertContext context);

  protected abstract String getUnresolvedMessage(String value);

  @NotNull
  public Collection<? extends List<T>> getVariants(final ConvertContext context) {
    return Collections.emptyList();
  }

  public static <T> void filterVariants(List<T> variants, GenericDomValue<List<T>> genericDomValue) {
    final List<T> list = genericDomValue.getValue();
    if (list != null) {
      for (Iterator<T> i = variants.iterator(); i.hasNext();) {
        final T variant = i.next();
        for (T existing: list) {
          if (existing.equals(variant)) {
            i.remove();
            break;
          }
        }
      }
    }
  }

  protected char getDefaultDelimiter() {
    return myDelimiters.charAt(0);
  }

  public List<T> fromString(@Nullable final String str, final ConvertContext context) {
    if (str == null) {
      return null;
    }
    List<T> values = new ArrayList<T>();

    for (String s : StringUtil.tokenize(str, myDelimiters)) {
      final T t = convertString(s.trim(), context);
      if (t != null) {
        values.add(t);
      }
    }
    return values;
  }

  public String toString(final List<T> ts, final ConvertContext context) {
    final StringBuffer buffer = new StringBuffer();
    final char delimiter = getDefaultDelimiter();
    for (T t : ts) {
      final String s = toString(t);
      if (s != null) {
        if (buffer.length() != 0) {
          buffer.append(delimiter);
        }
        buffer.append(s);
      }
    }
    return buffer.toString();
  }

  protected int skipDelimiters(String s, int pos) {
    while (pos < s.length()) {
      final char ch = s.charAt(pos);
      if (!isDelimiter(ch)) {
        break;
      }
      pos++;
    }
    return pos;
  }

  protected boolean isDelimiter(char ch) {
    return ch <= ' ' || myDelimiters.indexOf(ch) != -1;
  }

  @NotNull
  public PsiReference[] createReferences(final GenericDomValue<List<T>> genericDomValue,
                                         final PsiElement element,
                                         final ConvertContext context) {

    final String text = genericDomValue.getStringValue();
    if (text == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final int shift = 1;
    int start;
    int pos = 0;

    ArrayList<PsiReference> references = new ArrayList<PsiReference>();
    do {
      start = pos;
      pos = skipDelimiters(text, pos);
      if (pos == text.length()) {
        if (references.size() == 0) {
          references.add(createPsiReference(element, start + shift, pos + shift, context, genericDomValue));
        }
        break;
      }
      start = pos;
      while (++pos < text.length() && !isDelimiter(text.charAt(pos))) {}
      references.add(createPsiReference(element, start + shift, pos + shift, context, genericDomValue));
      pos++;
    } while(pos < text.length());

    return references.toArray(new PsiReference[references.size()]);
  }

  @NotNull
  protected PsiReference createPsiReference(final PsiElement element,
                                            int start, int end,
                                            final ConvertContext context,
                                            final GenericDomValue<List<T>> genericDomValue) {
    
    return new MyPsiReference(element, new TextRange(start, end), context, genericDomValue);
  }

  protected class MyPsiReference extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider {
    protected final ConvertContext myContext;
    protected final GenericDomValue<List<T>> myGenericDomValue;

    public MyPsiReference(final PsiElement element, final TextRange range, final ConvertContext context, final GenericDomValue<List<T>> genericDomValue) {
      this(element, range, context, genericDomValue, true);
    }

    public MyPsiReference(final PsiElement element, final TextRange range, final ConvertContext context, final GenericDomValue<List<T>> genericDomValue, boolean soft) {
      super(element, range, soft);
      myContext = context;
      myGenericDomValue = genericDomValue;
    }

    @Nullable
    public PsiElement resolve() {
      final String value = getValue();
      return resolveReference(convertString(value, myContext), myContext);
    }

    public Object[] getVariants() {
      return getReferenceVariants(myContext, myGenericDomValue);
    }

    public String getUnresolvedMessagePattern() {
      return getUnresolvedMessage(getValue());
    }
  }
}