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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;

@SuppressWarnings("DuplicatedCode")
final class PathBasedJdomXIncluder {
  private static final Logger LOG = Logger.getInstance(PathBasedJdomXIncluder.class);

  public static final PathResolver DEFAULT_PATH_RESOLVER = new PluginXmlPathResolver(Collections.emptyList());

  private static final @NonNls String INCLUDE = "include";
  private static final @NonNls String HREF = "href";
  private static final @NonNls String BASE = "base";
  private static final @NonNls String PARSE = "parse";
  private static final @NonNls String XML = "xml";
  private static final @NonNls String XPOINTER = "xpointer";

  private final DescriptorListLoadingContext context;
  private final PathResolver pathResolver;

  PathBasedJdomXIncluder(@NotNull DescriptorListLoadingContext context, @NotNull PathResolver pathResolver) {
    this.context = context;
    this.pathResolver = pathResolver;
  }

  private static boolean isIncludeElement(Element element) {
    return element.getName().equals(INCLUDE) && element.getNamespace().equals(JDOMUtil.XINCLUDE_NAMESPACE);
  }

  private @NotNull List<Element> resolveXIncludeElement(@NotNull DataLoader dataLoader,
                                                        @NotNull Element linkElement,
                                                        @Nullable String base,
                                                        @Nullable ArrayList<Element> result) throws JDOMException {
    String relativePath = linkElement.getAttributeValue(HREF);
    if (relativePath == null) {
      throw new RuntimeException("Missing href attribute");
    }

    String parseAttribute = linkElement.getAttributeValue(PARSE);
    if (parseAttribute != null) {
      LOG.assertTrue(parseAttribute.equals(XML), parseAttribute + " is not a legal value for the parse attribute");
    }

    Element remoteParsed = loadXIncludeReference(dataLoader, base, relativePath, linkElement);
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

    String childBase = getChildBase(base, relativePath);
    Iterator<Content> iterator = remoteParsed.getContent().iterator();
    while (iterator.hasNext()) {
      Content content = iterator.next();
      if (!(content instanceof Element)) {
        continue;
      }

      iterator.remove();

      Element element = (Element)content;
      if (isIncludeElement(element)) {
        resolveXIncludeElement(dataLoader, element, childBase, result);
      }
      else {
        resolveNonXIncludeElement(dataLoader, element, childBase);
        result.add(element);
      }
    }
    return result;
  }

  private static @Nullable String getChildBase(@Nullable String base, @NotNull String relativePath) {
    String childBase;
    int end = relativePath.lastIndexOf('/');
    if (end > 0) {
      if (relativePath.startsWith("/META-INF/")) {
        return base;
      }

      childBase = relativePath.substring(0, end);
      if (base != null) {
        childBase = base + "/" + childBase;
      }
    }
    else {
      childBase = base;
    }
    return childBase;
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

  private @Nullable Element loadXIncludeReference(@NotNull DataLoader dataLoader,
                                                  @Nullable String base,
                                                  @NotNull String relativePath,
                                                  @NotNull Element referrerElement) throws JDOMException {
    try {
      String explicitBase = referrerElement.getAttributeValue(BASE, Namespace.XML_NAMESPACE);
      if (explicitBase != null) {
        // to simplify implementation, no need to support obscure and not used base attribute
        LOG.error("Do not use xml:base attribute: " + explicitBase);
      }

      Element root = pathResolver.loadXIncludeReference(dataLoader, base, relativePath, context.getXmlFactory());
      if (root == null) {
        Element fallbackElement = referrerElement.getChild("fallback", referrerElement.getNamespace());
        if (fallbackElement != null) {
          return null;
        }

        if (context.ignoreMissingInclude) {
          LOG.info(relativePath + " include ignored (dataLoader=" + dataLoader + ")");
          return null;
        }
        else {
          throw new RuntimeException("Cannot resolve " + relativePath + " (dataLoader=" + dataLoader + ")");
        }
      }

      if (isIncludeElement(root)) {
        throw new UnsupportedOperationException("root tag of remote cannot be include");
      }
      else {
        resolveNonXIncludeElement(dataLoader, root, getChildBase(base, relativePath));
      }
      return root;
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
  }

  public void resolveNonXIncludeElement(@NotNull DataLoader dataLoader, @NotNull Element original, @Nullable String base)
    throws JDOMException {
    List<Content> contentList = original.getContent();
    for (int i = contentList.size() - 1; i >= 0; i--) {
      Content content = contentList.get(i);
      if (!(content instanceof Element)) {
        continue;
      }

      Element element = (Element)content;
      if (isIncludeElement(element)) {
        original.setContent(i, resolveXIncludeElement(dataLoader, element, base, null));
      }
      else {
        // process child element to resolve possible includes
        resolveNonXIncludeElement(dataLoader, element, base);
      }
    }
  }

  interface PathResolver {
    default boolean isFlat() {
      return false;
    }

    @Nullable Element loadXIncludeReference(@NotNull DataLoader dataLoader,
                                            @Nullable String base,
                                            @NotNull String relativePath,
                                            @NotNull SafeJdomFactory jdomFactory) throws IOException, JDOMException;

    @Nullable Element resolvePath(@NotNull DataLoader dataLoader, @NotNull String relativePath, @NotNull SafeJdomFactory jdomFactory)
      throws IOException, JDOMException;
  }
}