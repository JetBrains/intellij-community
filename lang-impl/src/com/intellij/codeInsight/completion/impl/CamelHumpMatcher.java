
/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class CamelHumpMatcher extends PrefixMatcher {
  private Pattern myPattern;
  private Perl5Matcher myMatcher;
  private final String myPrefix;

  public CamelHumpMatcher(@NotNull final String prefix) {
    myPrefix = prefix;
  }

  public boolean prefixMatches(@NotNull final String name) {
    if (myPattern == null) {
      myPattern = CompletionUtil.createCamelHumpsMatcher(myPrefix);
      myMatcher = new Perl5Matcher();
    }

    return myMatcher.matches(name, myPattern);
  }


  public boolean prefixMatches(@NotNull final LookupElement element) {
    final LookupItem<?> item = (LookupItem)element;
    final boolean itemCaseInsensitive = Boolean.TRUE.equals(item.getAttribute(LookupItem.CASE_INSENSITIVE));
    boolean result = false;
    for (final String name : item.getAllLookupStrings()) {
      if (itemCaseInsensitive && StringUtil.startsWithIgnoreCase(name, myPrefix) || prefixMatches(name)) {
        result = true;
        break;
      }
    }
    //todo dirty hack
    if (result && itemCaseInsensitive) {
      final String currentString = item.getLookupString();
      final String newString = handleCaseInsensitiveVariant(myPrefix, currentString);
      item.setLookupString(newString);
      if (item.getObject().equals(currentString)) {
        ((LookupItem)item).setObject(newString);
      }
    }
    return result;
  }

  @NotNull
  public String getPrefix() {
    return myPrefix;
  }

  @NotNull
  public PrefixMatcher cloneWithPrefix(@NotNull final String prefix) {
    assert prefix.startsWith(myPrefix) || myPrefix.startsWith(prefix) : "oldPrefix=" + myPrefix + "; new prefix=" + prefix;
    return new CamelHumpMatcher(prefix);
  }

  private static String handleCaseInsensitiveVariant(final String prefix, final String uniqueText) {
    final int length = prefix.length();
    if (length == 0) return uniqueText;
    boolean isAllLower = true;
    boolean isAllUpper = true;
    boolean sameCase = true;
    for (int i = 0; i < length && (isAllLower || isAllUpper || sameCase); i++) {
      final char c = prefix.charAt(i);
      isAllLower = isAllLower && Character.isLowerCase(c);
      isAllUpper = isAllUpper && Character.isUpperCase(c);
      sameCase = sameCase && Character.isLowerCase(c) == Character.isLowerCase(uniqueText.charAt(i));
    }
    if (sameCase) return uniqueText;
    if (isAllLower) return uniqueText.toLowerCase();
    if (isAllUpper) return uniqueText.toUpperCase();
    return uniqueText;
  }
}
