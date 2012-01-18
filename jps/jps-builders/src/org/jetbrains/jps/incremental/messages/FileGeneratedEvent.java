package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.util.Pair;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public class FileGeneratedEvent extends BuildMessage {

  private Collection<Pair<String, String>> myPaths = new ArrayList<Pair<String, String>>();

  public FileGeneratedEvent() {
    super("", Kind.INFO);
  }

  public void add(String root, String relativePath) {
    myPaths.add(new Pair<String, String>(root, relativePath));
  }

  public Collection<Pair<String, String>> getPaths() {
    return myPaths;
  }
}
