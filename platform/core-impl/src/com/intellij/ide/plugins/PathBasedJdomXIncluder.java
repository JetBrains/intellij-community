// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("DuplicatedCode")
final class PathBasedJdomXIncluder<T> {
  private static final Logger LOG = Logger.getInstance(PathBasedJdomXIncluder.class);

  public static final PathResolver<Path> DEFAULT_PATH_RESOLVER = new BasePathResolver();

  private static final Namespace XINCLUDE_NAMESPACE = Namespace.getNamespace("xi", "http://www.w3.org/2001/XInclude");
  private static final String INCLUDE = "include";
  private static final String HREF = "href";
  private static final String BASE = "base";
  private static final String PARSE = "parse";
  private static final String XML = "xml";
  private static final String XPOINTER = "xpointer";

  private final boolean ignoreMissing;
  private final PathResolver<T> pathResolver;

  private PathBasedJdomXIncluder(boolean ignoreMissing, @NotNull PathResolver<T> pathResolver) {
    this.ignoreMissing = ignoreMissing;
    this.pathResolver = pathResolver;
  }

  /**
   * Original element will be mutated in place.
   */
  public static <T> void resolveNonXIncludeElement(@NotNull Element original,
                                                   @Nullable Path base,
                                                   boolean ignoreMissing,
                                                   @NotNull PathResolver<T> pathResolver) {
    LOG.assertTrue(!isIncludeElement(original));

    new PathBasedJdomXIncluder<>(ignoreMissing, pathResolver).resolveNonXIncludeElement(original, pathResolver.createNewStack(base));
  }

  private static boolean isIncludeElement(Element element) {
    return element.getName().equals(INCLUDE) && element.getNamespace().equals(XINCLUDE_NAMESPACE);
  }

  @NotNull
  private List<Element> resolveXIncludeElement(@NotNull Element element, @NotNull List<T> bases) {
    String relativePath = element.getAttributeValue(HREF);
    if (relativePath == null) {
      throw new RuntimeException("Missing href attribute");
    }

    String parseAttribute = element.getAttributeValue(PARSE);
    if (parseAttribute != null) {
      LOG.assertTrue(parseAttribute.equals(XML), parseAttribute + " is not a legal value for the parse attribute");
    }

    List<Element> remoteParsed = parseRemote(bases, relativePath, element);
    if (!remoteParsed.isEmpty()) {
      remoteParsed = extractNeededChildren(element, remoteParsed);
    }

    int i = 0;
    for (; i < remoteParsed.size(); i++) {
      Element o = remoteParsed.get(i);
      if (isIncludeElement(o)) {
        List<Element> elements = resolveXIncludeElement(o, bases);
        remoteParsed.addAll(i, elements);
        i += elements.size() - 1;
        remoteParsed.remove(i);
      }
      else {
        resolveNonXIncludeElement(o, bases);
      }
    }

    for (Content content : remoteParsed) {
      content.detach();
    }
    return remoteParsed;
  }

  //xpointer($1)
  private static final Pattern XPOINTER_PATTERN = Pattern.compile("xpointer\\((.*)\\)");

  // /$1(/$2)?/*
  private static final Pattern CHILDREN_PATTERN = Pattern.compile("/([^/]*)(/[^/]*)?/\\*");

  @NotNull
  private static List<Element> extractNeededChildren(@NotNull Element element, @NotNull List<Element> remoteElements) {
    final String xpointer = element.getAttributeValue(XPOINTER);
    if (xpointer == null) {
      return remoteElements;
    }

    Matcher matcher = XPOINTER_PATTERN.matcher(xpointer);
    if (!matcher.matches()) {
      throw new RuntimeException("Unsupported XPointer: " + xpointer);
    }

    String pointer = matcher.group(1);
    matcher = CHILDREN_PATTERN.matcher(pointer);
    if (!matcher.matches()) {
      throw new RuntimeException("Unsupported pointer: " + pointer);
    }

    String rootTagName = matcher.group(1);

    assert remoteElements.size() == 1;
    Element e = remoteElements.get(0);
    if (!e.getName().equals(rootTagName)) {
      return Collections.emptyList();
    }

    String subTagName = matcher.group(2);
    if (subTagName != null) {
      // cut off the slash
      e = e.getChild(subTagName.substring(1));
      assert e != null;
    }
    return new ArrayList<>(e.getChildren());
  }

  @NotNull
  private List<Element> parseRemote(@NotNull List<T> bases, @NotNull String relativePath, @NotNull Element referrerElement) {
    try {
      int baseStackSize = bases.size();
      String base = referrerElement.getAttributeValue(BASE, Namespace.XML_NAMESPACE);
      if (base != null) {
        // to simplify implementation, no need to support obscure and not used base attribute
        LOG.error("Do not use xml:base attribute: " + base);
      }
      Element root = pathResolver.resolvePath(bases, relativePath, base);

      List<Element> list;
      if (isIncludeElement(root)) {
        list = resolveXIncludeElement(root, bases);
      }
      else {
        resolveNonXIncludeElement(root, bases);
        list = Collections.singletonList(root);
      }

      // stack not modified, if, for example, pathResolver resolves element not via filesystem
      if (baseStackSize != bases.size()) {
        bases.remove(bases.size() - 1);
      }
      return list;
    }
    catch (JDOMException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      Element fallbackElement = referrerElement.getChild("fallback", referrerElement.getNamespace());
      if (fallbackElement != null) {
        // TODO[yole] return contents of fallback element (we don't have fallback elements with content ATM)
        return Collections.emptyList();
      }
      else if (ignoreMissing) {
        LOG.info(relativePath + " include ignored: " + e.getMessage());
        return Collections.emptyList();
      }
      else {
        throw new RuntimeException(e);
      }
    }
  }

  private void resolveNonXIncludeElement(@NotNull Element original, @NotNull List<T> bases) {
    List<Content> contentList = original.getContent();
    for (int i = contentList.size() - 1; i >= 0; i--) {
      Content content = contentList.get(i);
      if (content instanceof Element) {
        Element element = (Element)content;
        if (isIncludeElement(element)) {
          original.setContent(i, resolveXIncludeElement(element, bases));
        }
        else {
          // process child element to resolve possible includes
          resolveNonXIncludeElement(element, bases);
        }
      }
    }
  }

  public interface PathResolver<T> {
    @NotNull
    Element resolvePath(@NotNull List<T> bases, @NotNull String relativePath, @Nullable String base) throws
                                                                                                      IOException,
                                                                                                      JDOMException;

    @NotNull
    List<T> createNewStack(@Nullable Path base);
  }
}