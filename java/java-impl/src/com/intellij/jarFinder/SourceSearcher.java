// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public abstract class SourceSearcher {
  private static final String MAVEN_POM_ENTRY_PREFIX = "META-INF/maven/";

  protected static @NotNull List<Element> findElements(@NotNull String expression, @NotNull Element element) {
    return XPathFactory.instance()
      .compile(expression, Filters.element())
      .evaluate(element);
  }

  /**
   * @return url of found artifact
   */
  protected @Nullable String findSourceJar(final @NotNull ProgressIndicator indicator, @NotNull String artifactId, @NotNull String version)
    throws SourceSearchException {
    return null;
  }

  /**
   * @param classesJar classes jar
   * @return url of found artifact
   */
  @Nullable
  public String findSourceJar(final @NotNull ProgressIndicator indicator,
                              @NotNull String artifactId,
                              @NotNull String version,
                              @NotNull VirtualFile classesJar) throws SourceSearchException {
    return findSourceJar(indicator, artifactId, version);
  }

  protected static @NotNull Element readElementCancelable(final ProgressIndicator indicator, String url) throws IOException {
    return HttpRequests.request(url)
      .accept("application/xml")
      .connect(new HttpRequests.RequestProcessor<>() {
        @Override
        public Element process(@NotNull HttpRequests.Request request) throws IOException {
          try {
            return JDOMUtil.load(request.getReader(indicator));
          }
          catch (JDOMException e) {
            throw new IOException(e);
          }
        }
      });
  }

  protected static @Nullable String findMavenGroupId(@NotNull VirtualFile classesJar, String artifactId) {
    try (JarFile jarFile = new JarFile(VfsUtilCore.virtualToIoFile(classesJar))) {
      final Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        final String name = entry.getName();
        if (StringUtil.startsWith(name, MAVEN_POM_ENTRY_PREFIX) && StringUtil.endsWith(name, "/" + artifactId + "/pom.xml")) {
          final int index = name.indexOf('/', MAVEN_POM_ENTRY_PREFIX.length());
          return index != -1 ? name.substring(MAVEN_POM_ENTRY_PREFIX.length(), index) : null;
        }
      }
    }
    catch (IOException ignore) {
    }
    return null;
  }
}