package com.intellij.jarFinder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MavenCentralSourceSearcher extends SourceSearcher {

  private static final Logger LOG = Logger.getInstance(MavenCentralSourceSearcher.class);

  @Nullable
  @Override
  protected String findSourceJar(@NotNull ProgressIndicator indicator,
                                               @NotNull String artifactId,
                                               @NotNull String version) throws SourceSearchException {
    try {
      indicator.setIndeterminate(true);
      indicator.setText("Connecting to http://search.maven.org");

      indicator.checkCanceled();

      String url = "http://search.maven.org/solrsearch/select?rows=3&wt=xml&q=a:%22" + artifactId + "%22%20AND%20v:%22" + version + "%22%20AND%20l:%22sources%22";
      Document document = readDocumentCancelable(indicator, url);

      indicator.checkCanceled();

      List<Element> artifactList = (List<Element>)XPath.newInstance("/response/result/doc/str[@name='g']").selectNodes(document);
      if (artifactList.isEmpty()) {
        return null;
      }

      Element element;

      if (artifactList.size() == 1) {
        element = artifactList.get(0);
      }
      else {
        // TODO handle
        return null;
      }

      String groupId = element.getValue();

      String downloadUrl = "http://search.maven.org/remotecontent?filepath=" + groupId.replace('.', '/') + '/' + artifactId + '/' + version + '/' + artifactId + '-' + version + "-sources.jar";

      return downloadUrl;
    }
    catch (JDOMException e) {
      LOG.warn(e);
      throw new SourceSearchException("Failed to parse response from server. See log for more details.");
    }
    catch (IOException e) {
      indicator.checkCanceled(); // Cause of IOException may be canceling of operation.

      LOG.warn(e);
      throw new SourceSearchException("Connection problem. See log for more details.");
    }
  }
}
