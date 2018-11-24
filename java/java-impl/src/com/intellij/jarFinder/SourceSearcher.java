/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.jarFinder;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.JdomKt;
import com.intellij.util.io.HttpRequests;
import org.jdom.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author Sergey Evdokimov
 */
public abstract class SourceSearcher {

  private static final String MAVEN_POM_ENTRY_PREFIX = "META-INF/maven/";

  /**
   * @param indicator
   * @param artifactId
   * @param version
   * @return url of found artifact
   */
  @Nullable
  protected String findSourceJar(@NotNull final ProgressIndicator indicator, @NotNull String artifactId, @NotNull String version)
    throws SourceSearchException {
    return null;
  }

  /**
   * @param indicator
   * @param artifactId
   * @param version
   * @param classesJar classes jar
   * @return url of found artifact
   */
  @Nullable
  protected String findSourceJar(@NotNull final ProgressIndicator indicator,
                                 @NotNull String artifactId,
                                 @NotNull String version,
                                 @NotNull VirtualFile classesJar) throws SourceSearchException {
    return findSourceJar(indicator, artifactId, version);
  }

  @NotNull
  protected static Document readDocumentCancelable(final ProgressIndicator indicator, String url) throws IOException {
    return HttpRequests.request(url)
      .accept("application/xml")
      .connect(new HttpRequests.RequestProcessor<Document>() {
        @Override
        public Document process(@NotNull HttpRequests.Request request) throws IOException {
          return JdomKt.loadDocument(request.getReader(indicator));
        }
      });
  }

  @Nullable
  protected static String findMavenGroupId(@NotNull VirtualFile classesJar, String artifactId) {
    try {
      JarFile jarFile = new JarFile(VfsUtilCore.virtualToIoFile(classesJar));
      try {
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
      finally {
        try {
          jarFile.close();
        }
        catch (IOException ignore) {
        }
      }
    }
    catch (IOException ignore) {
    }
    return null;
  }
}

class SourceSearchException extends Exception {

  SourceSearchException(String message) {
    super(message);
  }
}