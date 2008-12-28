
/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class CamelHumpMatcher extends PrefixMatcher {
  private Pattern myPattern;
  private Perl5Matcher myMatcher;
  private final String myPrefix;
  private final boolean myCaseSensitive;

  public CamelHumpMatcher(@NotNull final String prefix) {
    this(prefix, true);
  }

  public CamelHumpMatcher(String prefix, boolean caseSensitive) {
    myPrefix = prefix;
    myCaseSensitive = caseSensitive;
  }

  public synchronized boolean prefixMatches(@NotNull final String name) {
    if (myPattern == null) {
      myPattern = createCamelHumpsMatcher();
      myMatcher = new Perl5Matcher();
    }

    return myMatcher.matches(name, myPattern);
  }


  public boolean prefixMatches(@NotNull final LookupElement element) {
    final boolean itemCaseInsensitive = element instanceof LookupItem && 
                                        Boolean.TRUE.equals(((LookupItem)element).getAttribute(LookupItem.CASE_INSENSITIVE));
    boolean result = prefixMatchersInternal(element, itemCaseInsensitive);

    //todo dirty hack
    if (result && itemCaseInsensitive) {
      final String currentString = ContainerUtil.find(element.getAllLookupStrings(), new Condition<String>() {
        public boolean value(final String s) {
          return StringUtil.startsWithIgnoreCase(s, myPrefix);
        }
      });
      if (currentString != null) {
        final String newString = handleCaseInsensitiveVariant(myPrefix, currentString);
        final LookupItem<?> item = (LookupItem)element;
        item.setLookupString(newString);
        if (item.getObject().equals(currentString)) {
          ((LookupItem)item).setObject(newString);
        }
      }
    }
    return result;
  }

  private boolean prefixMatchersInternal(final LookupElement element, final boolean itemCaseInsensitive) {
    for (final String name : element.getAllLookupStrings()) {
      if (itemCaseInsensitive && StringUtil.startsWithIgnoreCase(name, myPrefix) || prefixMatches(name)) {
        return true;
      }
      if (itemCaseInsensitive && CodeInsightSettings.ALL != CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE && name.length() > 0) {
        final char c = name.charAt(0);
        String swappedCase = (Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c)) + name.substring(1);
        if (prefixMatches(swappedCase)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public String getPrefix() {
    return myPrefix;
  }

  @NotNull
  public PrefixMatcher cloneWithPrefix(@NotNull final String prefix) {
    return new CamelHumpMatcher(prefix);
  }

  private static String handleCaseInsensitiveVariant(final String prefix, @NotNull final String uniqueText) {
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

  private Pattern createCamelHumpsMatcher() {
    Perl5Compiler compiler = new Perl5Compiler();
    try {
      if (!myCaseSensitive) {
        return compiler.compile(NameUtil.buildRegexp(myPrefix, 0, true, true));
      }

      final CodeInsightSettings settings = CodeInsightSettings.getInstance();
      int variant = settings.COMPLETION_CASE_SENSITIVE;

      switch (variant) {
        case CodeInsightSettings.NONE:
          return compiler.compile(NameUtil.buildRegexp(myPrefix, 0, true, true));
        case CodeInsightSettings.FIRST_LETTER:
          return compiler.compile(NameUtil.buildRegexp(myPrefix, 1, true, true));
        case CodeInsightSettings.ALL:
          return compiler.compile(NameUtil.buildRegexp(myPrefix, 0, false, false));
        default:
          return compiler.compile(NameUtil.buildRegexp(myPrefix, 1, true, false));
      }
    }
    catch (MalformedPatternException me) {
      throw new RuntimeException(me);
    }
  }
}
