package com.intellij.jarFinder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
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
                                 @NotNull String version,
                                 @NotNull VirtualFile classesJar) throws SourceSearchException {
    try {
      indicator.setText("Connecting to https://search.maven.org");

      indicator.checkCanceled();

      String url = "https://search.maven.org/solrsearch/select?rows=3&wt=xml&q=";
      final String groupId = findMavenGroupId(classesJar, artifactId);
      if (groupId != null) {
        url += "g:%22" + groupId + "%22%20AND%20";
      }
      url += "a:%22" + artifactId + "%22%20AND%20v:%22" + version + "%22%20AND%20l:%22sources%22";
      @SuppressWarnings("unchecked")
      List<Element> artifactList = (List<Element>)XPath.newInstance("/response/result/doc/str[@name='g']").selectNodes(readDocumentCancelable(indicator, url));
      if (artifactList.isEmpty()) {
        return null;
      }

      if (artifactList.size() == 1) {
        return "https://search.maven.org/remotecontent?filepath=" +
               artifactList.get(0).getValue().replace('.', '/') + '/' +
               artifactId + '/' +
               version + '/' +
               artifactId + '-' +
               version + "-sources.jar";
      }
      else {
        // TODO handle
        return null;
      }
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
