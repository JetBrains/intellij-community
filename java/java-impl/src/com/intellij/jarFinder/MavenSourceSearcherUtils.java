// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarFinder;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.HttpRequests;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.filter2.Filters;
import org.jdom.xpath.XPathFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author Edoardo Luppi
 */
public class MavenSourceSearcherUtils {
  private static final String MAVEN_POM_ENTRY_PREFIX = "META-INF/maven/";

  @Nullable
  public static String findMavenGroupId(@NotNull final VirtualFile classesJar, @NotNull final String artifactId) throws IOException {
    try (final JarFile jarFile = new JarFile(VfsUtilCore.virtualToIoFile(classesJar))) {
      final Enumeration<JarEntry> entries = jarFile.entries();

      while (entries.hasMoreElements()) {
        final JarEntry entry = entries.nextElement();
        final String name = entry.getName();

        if (StringUtil.startsWith(name, MAVEN_POM_ENTRY_PREFIX) &&
            StringUtil.endsWith(name, "/" + artifactId + "/pom.xml")) {
          final int index = name.indexOf('/', MAVEN_POM_ENTRY_PREFIX.length());
          return index != -1
                 ? name.substring(MAVEN_POM_ENTRY_PREFIX.length(), index)
                 : null;
        }
      }
    }

    return null;
  }

  @NotNull
  public static List<Element> findElements(
    @NotNull final String expression,
    @NotNull final Element element) {
    return XPathFactory.instance()
      .compile(expression, Filters.element())
      .evaluate(element);
  }

  @NotNull
  public static Element readElementCancelable(
    @NotNull final ProgressIndicator indicator,
    @NotNull final String url) throws IOException {
    return HttpRequests.request(url)
      .accept("application/xml")
      .connect(request -> {
        try {
          return JDOMUtil.load(request.getReader(indicator));
        }
        catch (final JDOMException e) {
          throw new IOException(e);
        }
      });
  }
}
