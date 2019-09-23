// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.openapi.diagnostic.Logger;
import org.jdom.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public abstract class PathMacroMap {
  private static final Logger LOG = Logger.getInstance(PathMacroMap.class);

  @NotNull
  public abstract String substitute(@NotNull String text, boolean caseSensitive);

  @NotNull
  public final String substitute(@NotNull String text, boolean caseSensitive, boolean recursively) {
    return recursively
           ? substituteRecursively(text, caseSensitive)
           : substitute(text, caseSensitive);
  }

  public final void substitute(@NotNull Element e, boolean caseSensitive) {
    substitute(e, caseSensitive, false);
  }

  public final void substitute(@NotNull Element element, boolean caseSensitive, boolean recursively, @Nullable PathMacroFilter filter) {
    if (filter != null && filter.skipPathMacros(element)) {
      return;
    }

    for (Content child : element.getContent()) {
      if (child instanceof Element) {
        substitute((Element)child, caseSensitive, recursively, filter);
      }
      else if (child instanceof Text) {
        Text t = (Text)child;
        String oldText = t.getText();
        String newText = recursively ? substituteRecursively(oldText, caseSensitive) : substitute(oldText, caseSensitive);
        if (oldText != newText) {
          // it is faster to call 'setText' right away than perform additional 'equals' check
          t.setText(newText);
        }
      }
      else if (!(child instanceof Comment)) {
        LOG.error("Wrong content: " + child.getClass());
      }
    }

    if (!element.hasAttributes()) {
      return;
    }

    for (Attribute attribute : element.getAttributes()) {
      if (filter == null || !filter.skipPathMacros(attribute)) {
        String newValue = getAttributeValue(attribute, filter, caseSensitive, recursively);
        if (attribute.getValue() != newValue) {
          // it is faster to call 'setValue' right away than perform additional 'equals' check
          attribute.setValue(newValue);
        }
      }
    }
  }

  @NotNull
  public String getAttributeValue(@NotNull Attribute attribute, @Nullable PathMacroFilter filter, boolean caseSensitive, boolean recursively) {
    String oldValue = attribute.getValue();
    if (recursively || (filter != null && filter.recursePathMacros(attribute))) {
      return substituteRecursively(oldValue, caseSensitive);
    }
    else {
      return substitute(oldValue, caseSensitive);
    }
  }

  public final void substitute(@NotNull Element e, boolean caseSensitive, final boolean recursively) {
    substitute(e, caseSensitive, recursively, null);
  }

  @NotNull
  public String substituteRecursively(@NotNull String text, boolean caseSensitive) {
    return substitute(text, caseSensitive);
  }

  public abstract int hashCode();
}
