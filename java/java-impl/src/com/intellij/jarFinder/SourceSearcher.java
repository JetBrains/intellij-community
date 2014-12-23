package com.intellij.jarFinder;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.io.HttpRequests;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public abstract class SourceSearcher {
  /**
   * @param indicator
   * @param artifactId
   * @param version
   * @return groupId of found artifact and url.
   */
  @Nullable
  protected abstract String findSourceJar(@NotNull final ProgressIndicator indicator, @NotNull String artifactId, @NotNull String version) throws SourceSearchException;

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
}

class SourceSearchException extends Exception {

  SourceSearchException(String message) {
    super(message);
  }
}