package org.jetbrains.jps.incremental;

import org.jetbrains.jps.Module;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/8/11
 */
public class FSSnapshot {
  private final List<Root> myRoots = new ArrayList<Root>();
  private final Module myModule;

  public FSSnapshot(Module module) {
    myModule = module;
  }

  public Root addRoot(File file, String path) {
    final Root root = new Root(new Node(file), path);
    myRoots.add(root);
    return root;
  }

  public boolean processFiles(FileProcessor processor) throws Exception {
    for (Root root : myRoots) {
      if (!processRecursively(root.node, processor, root.path)) {
        return false;
      }
    }
    return true;
  }

  private boolean processRecursively(Node from, FileProcessor processor, String srcRoot) throws Exception {
    if (from.isDirectory()) {
      for (Node child : from.children) {
        if (!processRecursively(child, processor, srcRoot)) {
          return false;
        }
      }
      return true;
    }
    return processor.apply(myModule, from.file, srcRoot);
  }

  static class Root {
    private final Node node;
    private final String path;

    private Root(Node node, String path) {
      this.node = node;
      this.path = path;
    }

    public Node getNode() {
      return node;
    }

    public String getPath() {
      return path;
    }
  }

  static class Node {
    private final File file;
    private final boolean myIsDirectory;
    private final List<Node> children = new ArrayList<Node>();

    Node(File file) {
      this.file = file;
      myIsDirectory = file.isDirectory();
    }

    public Node addChild(File file) {
      final Node node = new Node(file);
      children.add(node);
      return node;
    }

    public File getFile() {
      return file;
    }

    public List<Node> getChildren() {
      return Collections.unmodifiableList(children);
    }

    public boolean isDirectory() {
      return myIsDirectory;
    }
  }

}
