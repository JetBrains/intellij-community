package com.intellij.jarFinder;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.net.HttpConfigurable;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

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

  protected static Document readDocumentCancelable(final ProgressIndicator indicator, String url) throws JDOMException, IOException {
    final HttpURLConnection urlConnection = HttpConfigurable.getInstance().openHttpConnection(url);

    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          //noinspection InfiniteLoopStatement
          while (true) {
            if (indicator.isCanceled()) {
              urlConnection.disconnect();
            }

            //noinspection BusyWait
            Thread.sleep(100);
          }
        }
        catch (InterruptedException ignored) {

        }
      }
    });

    t.start();

    try {
      urlConnection.setRequestProperty("accept", "application/xml");

      InputStream inputStream = urlConnection.getInputStream();
      try {
        return new SAXBuilder().build(inputStream);
      }
      finally {
        inputStream.close();
      }
    }
    finally {
      t.interrupt();
    }
  }
}

class SourceSearchException extends Exception {

  SourceSearchException(String message) {
    super(message);
  }

}