/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides utility methods for building documentation preview.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/10/12 8:06 AM
 */
public class DocPreviewUtil {

  private DocPreviewUtil() {
  }

  /**
   * Allows to build a documentation preview from the given arguments. Basically, takes given 'header' text and tries to modify
   * it by using hyperlink information encapsulated at the given 'full text'.
   * 
   * @param header                     target documentation header. Is expected to be a result of the
   *                                   {@link DocumentationProvider#getQuickNavigateInfo(PsiElement, PsiElement)} call
   * @param qName                      there is a possible case that not all documentation text will be included to the preview
   *                                   (according to the given 'desired rows and columns per-row' arguments). A link that points to the
   *                                   element with the given qualified name is added to the preview's end if the qName is provided then
   * @param fullText                   full documentation text (if available)
   */
  @NotNull
  public static String buildPreview(@NotNull final String header, @Nullable final String qName, @Nullable final String fullText) {
    if (fullText == null) {
      return header;
    }
    
    // Build links info.
    Map<String/*qName*/, String/*address*/> links = new HashMap<String, String>();
    process(fullText, new LinksCollector(links));
    if (qName != null) {
      links.put(qName, DocumentationManager.PSI_ELEMENT_PROTOCOL + qName);
    }

    // Apply links info to the header template.
    String result = header.replace("\n", "<br/>");
    for (Map.Entry<String, String> entry : links.entrySet()) {
      String visibleName = entry.getKey();
      int i = visibleName.lastIndexOf('.');
      if (i > 0 && i < visibleName.length() - 1) {
        visibleName = visibleName.substring(i + 1);
      }
      result = result.replace(entry.getKey(), String.format("<a href=\"%s\">%s</a>", entry.getValue(), visibleName));
    }
    return result;
  }

  private enum State {TEXT, INSIDE_OPEN_TAG, INSIDE_CLOSE_TAG}
  
  @SuppressWarnings("AssignmentToForLoopParameter")
  private static int process(@NotNull String text, @NotNull Callback callback) {
    State state = State.TEXT;
    int dataStartOffset = 0;
    int tagNameStartOffset = 0;
    String tagName = null;
    int i = 0;
    for (; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (state) {
        case TEXT:
          if (c == '<') {
            if (i > dataStartOffset) {
              if (!callback.onText(text.substring(dataStartOffset, i).replace("&nbsp;", " "))) {
                return dataStartOffset;
              }
            }
            dataStartOffset = i;
            if (i < text.length() - 1 && text.charAt(i + 1) == '/') {
              state = State.INSIDE_CLOSE_TAG;
              tagNameStartOffset = ++i + 1;
            }
            else {
              state = State.INSIDE_OPEN_TAG;
              tagNameStartOffset = i + 1;
            }
          }
          break;
        case INSIDE_OPEN_TAG:
          if (c == ' ') {
            tagName = text.substring(tagNameStartOffset, i);
          }
          else if (c == '/') {
            if (i < text.length() - 1 && text.charAt(i + 1) == '>') {
              if (tagName == null) {
                tagName = text.substring(tagNameStartOffset, i);
              }
              if (!callback.onStandaloneTag(tagName, text.substring(dataStartOffset, i + 2))) {
                return dataStartOffset;
              }
              tagName = null;
              state = State.TEXT;
              dataStartOffset = ++i + 1;
              break;
            }
          }
          else if (c == '>') {
            if (tagName == null) {
              tagName = text.substring(tagNameStartOffset, i);
            }
            if (!callback.onOpenTag(tagName, text.substring(dataStartOffset, i + 1))) {
              return dataStartOffset;
            }
            tagName = null;
            state = State.TEXT;
            dataStartOffset = i + 1;
          }
          break;
        case INSIDE_CLOSE_TAG:
          if (c == '>') {
            if (tagName == null) {
              tagName = text.substring(tagNameStartOffset, i);
            }
            if (!callback.onCloseTag(tagName, text.substring(dataStartOffset, i + 1))) {
              return dataStartOffset;
            }
            tagName = null;
            state = State.TEXT;
            dataStartOffset = i + 1;
          }
      }
    }

    if (dataStartOffset < text.length()) {
      callback.onText(text.substring(dataStartOffset, text.length()).replace("&nbsp;", " "));
    }
    
    return i;
  }

  private interface Callback {
    boolean onOpenTag(@NotNull String name, @NotNull String text);
    boolean onCloseTag(@NotNull String name, @NotNull String text);
    boolean onStandaloneTag(@NotNull String name, @NotNull String text);
    boolean onText(@NotNull String text);
  }

  private static class LinksCollector implements Callback {

    private static final Pattern HREF_PATTERN = Pattern.compile("href=[\"']([^\"']+)");

    @NotNull private final Map<String, String> myLinks;
    private                String              myHref;

    LinksCollector(@NotNull Map<String, String> links) {
      myLinks = links;
    }

    @Override
    public boolean onOpenTag(@NotNull String name, @NotNull String text) {
      if (!"a".equals(name)) {
        return true;
      }
      Matcher matcher = HREF_PATTERN.matcher(text);
      if (matcher.find()) {
        myHref = matcher.group(1);
      }
      return true;
    }

    @Override
    public boolean onCloseTag(@NotNull String name, @NotNull String text) {
      if ("a".equals(name)) {
        myHref = null;
      }
      return true;
    }

    @Override
    public boolean onStandaloneTag(@NotNull String name, @NotNull String text) {
      return true;
    }

    @Override
    public boolean onText(@NotNull String text) {
      if (myHref != null) {
        myLinks.put(text, myHref);
        myHref = null;
      }
      return true;
    }
  }
}
