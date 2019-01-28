// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarFinder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class SonatypeSourceSearcher extends SourceSearcher {
  private static final Logger LOG = Logger.getInstance(SonatypeSourceSearcher.class);

  @Nullable
  @Override
  public String findSourceJar(@NotNull final ProgressIndicator indicator,
                              @NotNull String artifactId,
                              @NotNull String version,
                              @NotNull VirtualFile classesJar)
    throws SourceSearchException {
    try {
      indicator.setIndeterminate(true);
      indicator.setText("Connecting to https://oss.sonatype.org");

      indicator.checkCanceled();

      String url = "https://oss.sonatype.org/service/local/lucene/search?collapseresults=true&c=sources&a=" + artifactId + "&v=" + version;
      String groupId = findMavenGroupId(classesJar, artifactId);
      if(groupId != null) {
        url += ("&g=" + groupId);
      }

      List<Element> artifactList = findElements("./data/artifact", readElementCancelable(indicator, url));
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

      List<Element> artifactHintList = findElements("artifactHits/artifactHit/artifactLinks/artifactLink/classifier[text()='sources']/../../..", element);
      if (artifactHintList.isEmpty()) {
        return null;
      }

      groupId = element.getChildTextTrim("groupId");
      String repositoryId = artifactHintList.get(0).getChildTextTrim("repositoryId");

      return "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=" +
                           repositoryId + "&g=" +
                           groupId + "&a=" +
                           artifactId + "&v=" +
                           version + "&e=jar&c=sources";
    }
    catch (IOException e) {
      indicator.checkCanceled(); // Cause of IOException may be canceling of operation.

      LOG.warn(e);
      throw new SourceSearchException("Connection problem. See log for more details.");
    }
  }
}
