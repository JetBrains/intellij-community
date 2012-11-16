package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public class FileGeneratedEvent extends BuildMessage {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.messages.FileGeneratedEvent");

  private final Collection<Pair<String, String>> myPaths = new ArrayList<Pair<String, String>>();

  public FileGeneratedEvent() {
    super("", Kind.INFO);
  }

  public void add(String root, String relativePath) {
    if (root != null && relativePath != null) {
      myPaths.add(new Pair<String, String>(FileUtil.toSystemIndependentName(root), FileUtil.toSystemIndependentName(relativePath)));
    }
    else {
      LOG.info("Invalid file generation event: root=" + root + "; relativePath=" + relativePath);
    }
  }

  public Collection<Pair<String, String>> getPaths() {
    return myPaths;
  }
}
