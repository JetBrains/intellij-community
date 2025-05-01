// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarFinder;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public class MavenCentralSourceSearcher extends SourceSearcher {
  private static final Logger LOG = Logger.getInstance(MavenCentralSourceSearcher.class);

  @Override
  @Nullable
  public String findSourceJar(@NotNull ProgressIndicator indicator,
                              @NotNull String artifactId,
                              @NotNull String version,
                              @NotNull VirtualFile classesJar) throws SourceSearchException {
    try {
      indicator.setText(IdeCoreBundle.message("progress.message.connecting.to", "https://search.maven.org"));

      indicator.checkCanceled();

      String url = "https://search.maven.org/solrsearch/select?rows=3&wt=xml&q=";
      final String groupId = findMavenGroupId(classesJar, artifactId);
      if (groupId != null) {
        url += "g:%22" + groupId + "%22%20AND%20";
      }
      url += "a:%22" + artifactId + "%22%20AND%20v:%22" + version + "%22%20AND%20l:%22sources%22";
      List<Element> artifactList = findElements("./result/doc/str[@name='g']", readElementCancelable(indicator, url));
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
    catch (IOException e) {
      indicator.checkCanceled(); // Cause of IOException may be canceling of operation.

      LOG.warn(e);
      throw new SourceSearchException("Connection problem. See log for more details.");
    }
  }
}
