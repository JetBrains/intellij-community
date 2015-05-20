package com.intellij.jarFinder;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.HttpRequests;
import org.jdom.Document;
import org.jdom.JDOMException;
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
          try {
            return JDOMUtil.loadDocument(request.getReader(indicator));
          }
          catch (JDOMException e) {
            throw new IOException(e);
          }
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