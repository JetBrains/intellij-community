// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SafeJdomFactory;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;

@SuppressWarnings("DuplicatedCode")
final class PathBasedJdomXIncluder<T> {
  private static final Logger LOG = Logger.getInstance(PathBasedJdomXIncluder.class);

  public static final PathResolver<Path> DEFAULT_PATH_RESOLVER = new BasePathResolver();

  private static final @NonNls String INCLUDE = "include";
  private static final @NonNls String HREF = "href";
  private static final @NonNls String BASE = "base";
  private static final @NonNls String PARSE = "parse";
  private static final @NonNls String XML = "xml";
  private static final @NonNls String XPOINTER = "xpointer";

  private final DescriptorListLoadingContext context;
  private final PathResolver<T> pathResolver;

  private PathBasedJdomXIncluder(@NotNull DescriptorListLoadingContext context, @NotNull PathResolver<T> pathResolver) {
    this.context = context;
    this.pathResolver = pathResolver;
  }

  /**
   * Original element will be mutated in place.
   */
  public static <T> void resolveNonXIncludeElement(@NotNull Element original,
                                                   @Nullable Path base,
                                                   @NotNull DescriptorListLoadingContext context,
                                                   @NotNull PathResolver<T> pathResolver) {
    LOG.assertTrue(!isIncludeElement(original));
    new PathBasedJdomXIncluder<>(context, pathResolver).resolveNonXIncludeElement(original, pathResolver.createNewStack(base));
  }

  private static boolean isIncludeElement(Element element) {
    return element.getName().equals(INCLUDE) && element.getNamespace().equals(JDOMUtil.XINCLUDE_NAMESPACE);
  }

  private @NotNull List<Element> resolveXIncludeElement(@NotNull Element linkElement, @NotNull List<T> bases, @Nullable ArrayList<Element> result) {
    String relativePath = linkElement.getAttributeValue(HREF);
    if (relativePath == null) {
      throw new RuntimeException("Missing href attribute");
    }

    String parseAttribute = linkElement.getAttributeValue(PARSE);
    if (parseAttribute != null) {
      LOG.assertTrue(parseAttribute.equals(XML), parseAttribute + " is not a legal value for the parse attribute");
    }

    Element remoteParsed = loadXIncludeReference(bases, relativePath, linkElement);
    if (remoteParsed != null) {
      String xpointer = linkElement.getAttributeValue(XPOINTER);
      if (xpointer != null) {
        remoteParsed = extractNeededChildren(remoteParsed, xpointer);
      }
    }
    if (remoteParsed == null) {
      return result == null ? Collections.emptyList() : result;
    }

    if (result == null) {
      result = new ArrayList<>(remoteParsed.getContentSize());
    }
    else {
      result.ensureCapacity(result.size() + remoteParsed.getContentSize());
    }

    Iterator<Content> iterator = remoteParsed.getContent().iterator();
    while (iterator.hasNext()) {
      Content content = iterator.next();
      if (!(content instanceof Element)) {
        continue;
      }

      iterator.remove();

      Element element = (Element)content;
      if (isIncludeElement(element)) {
        resolveXIncludeElement(element, bases, result);
      }
      else {
        resolveNonXIncludeElement(element, bases);
        result.add(element);
      }
    }
    return result;
  }

  private static @Nullable Element extractNeededChildren(@NotNull Element remoteElement, @NotNull String xpointer) {
    Matcher matcher = JDOMUtil.XPOINTER_PATTERN.matcher(xpointer);
    if (!matcher.matches()) {
      throw new RuntimeException("Unsupported XPointer: " + xpointer);
    }

    String pointer = matcher.group(1);
    matcher = JDOMUtil.CHILDREN_PATTERN.matcher(pointer);
    if (!matcher.matches()) {
      throw new RuntimeException("Unsupported pointer: " + pointer);
    }

    Element result = remoteElement;
    if (!result.getName().equals(matcher.group(1))) {
      return null;
    }

    String subTagName = matcher.group(2);
    if (subTagName != null) {
      // cut off the slash
      result = result.getChild(subTagName.substring(1));
      assert result != null;
    }
    return result;
  }

  private @Nullable Element loadXIncludeReference(@NotNull List<T> bases, @NotNull String relativePath, @NotNull Element referrerElement) {
    int baseStackSize = bases.size();
    try {
      String base = referrerElement.getAttributeValue(BASE, Namespace.XML_NAMESPACE);
      if (base != null) {
        // to simplify implementation, no need to support obscure and not used base attribute
        LOG.error("Do not use xml:base attribute: " + base);
      }
      Element root = pathResolver.loadXIncludeReference(bases, relativePath, base, context.getXmlFactory());
      if (isIncludeElement(root)) {
        throw new UnsupportedOperationException("root tag of remote cannot be include");
      }
      else {
        resolveNonXIncludeElement(root, bases);
      }
      return root;
    }
    catch (JDOMException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      Element fallbackElement = referrerElement.getChild("fallback", referrerElement.getNamespace());
      if (fallbackElement != null) {
        // we don't have fallback elements with content ATM
        return null;
      }
      else if (context.ignoreMissingInclude) {
        LOG.info(relativePath + " include ignored: " + e.getMessage());
        return null;
      }
      else {
        throw new RuntimeException(e);
      }
    }
    finally {
      // stack not modified, if, for example, pathResolver resolves element not via filesystem
      if (baseStackSize != bases.size()) {
        bases.remove(bases.size() - 1);
      }
    }
  }

  private void resolveNonXIncludeElement(@NotNull Element original, @NotNull List<T> bases) {
    List<Content> contentList = original.getContent();
    for (int i = contentList.size() - 1; i >= 0; i--) {
      Content content = contentList.get(i);
      if (!(content instanceof Element)) {
        continue;
      }

      Element element = (Element)content;
      if (isIncludeElement(element)) {
        original.setContent(i, resolveXIncludeElement(element, bases, null));
      }
      else {
        // process child element to resolve possible includes
        resolveNonXIncludeElement(element, bases);
      }
    }
  }

  interface PathResolver<T> {
    default boolean isFlat() {
      return false;
    }

    @NotNull Element loadXIncludeReference(@NotNull List<T> bases,
                                           @NotNull String relativePath,
                                           @Nullable String base,
                                           @NotNull SafeJdomFactory jdomFactory) throws IOException, JDOMException;

    @NotNull Element resolvePath(@NotNull Path basePath, @NotNull String relativePath, @NotNull SafeJdomFactory jdomFactory)
      throws IOException, JDOMException;

    @NotNull List<T> createNewStack(@Nullable Path base);
  }
}