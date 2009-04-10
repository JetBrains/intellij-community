package com.intellij.compiler.impl;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 6, 2009
 */
public class OutputPathFinder {
  private final Node myRoot = new Node("");

  public OutputPathFinder(Set<File> outputDirs) {
    for (File dir : outputDirs) {
      final String path = FileUtil.toSystemIndependentName(dir.getPath());
      Node node = myRoot;
      int idx = path.startsWith("/")? 1 : 0;
      int slashIndex = path.indexOf('/', idx);

      for (; slashIndex >= 0; slashIndex = path.indexOf('/', idx)) {
        final String name = path.substring(idx, slashIndex);
        node = node.addChild(name);
        idx = slashIndex + 1;
      }

      if (idx < path.length()) {
        node = node.addChild(path.substring(idx));
      }

      assert node.isLeaf();
      
      node.setData(path);
    }
  }

  @Nullable
  public String lookupOutputPath(File outputFile) {
    return lookupOutputPath(outputFile.getPath());
  }

  @Nullable
  public String lookupOutputPath(String filePath) {
    final String path = FileUtil.toSystemIndependentName(filePath);
    Node node = myRoot;
    int idx = path.startsWith("/")? 1 : 0;
    int slashIndex = path.indexOf('/', idx);

    for (; slashIndex >= 0; slashIndex = path.indexOf('/', idx)) {
      final String name = path.substring(idx, slashIndex);
      node = node.getChild(name);
      if (node == null) {
        return null;
      }
      if (node.isLeaf()) {
        return node.getData();
      }
      idx = slashIndex + 1;
    }

    if (idx < path.length()) {
      node = node.getChild(path.substring(idx));
    }
    return (node != null && node.isLeaf()) ? node.getData() : null;
  }

  private static class Node {
    private final String myName;
    @Nullable
    private Object myChildren; // either a Node or a Map<String, Node> or a String for leaf nodes

    private Node(String name) {
      myName = name;
    }

    public String getName() {
      return myName;
    }

    public boolean isLeaf() {
      return myChildren == null || myChildren instanceof String;
    }

    @Nullable
    public String getData() {
      return isLeaf()? (String)myChildren : null;
    }

    public void setData(String path) {
      myChildren = path;
    }

    public Node addChild(String childName) {
      if (myChildren == null) {
        final Node node = new Node(childName);
        myChildren = node;
        return node;
      }

      final Map<String, Node> map;
      if (myChildren instanceof Node)  {
        final Node existingChild = (Node)myChildren;
        myChildren = map = new HashMap<String, Node>();
        map.put(existingChild.getName(), existingChild);
      }
      else {
        //noinspection unchecked
        map = (Map<String, Node>)myChildren;
      }

      Node node = map.get(childName);
      if (node == null) {
        map.put(childName, node = new Node(childName));
      }
      return node;
    }

    @Nullable
    public Node getChild(String childName) {
      if (myChildren instanceof Node) {
        final Node childNode = (Node)myChildren;
        if (childName.equals(childNode.getName())) {
          return childNode;
        }
      }

      return isLeaf()? null : ((Map<String, Node>)myChildren).get(childName);
    }
  }


  public static void main(String[] args) {
    final Set<File> set = new HashSet<File>();
    set.add(new File("/a/b/c"));
    set.add(new File("a/b/d"));
    set.add(new File("a/b/e"));
    set.add(new File("/a/b/f/g"));

    final OutputPathFinder finder = new OutputPathFinder(set);

    System.out.println(finder.lookupOutputPath(new File("a/b/c/dir1/dir2/File.class")));
    System.out.println(finder.lookupOutputPath(new File("a/b/d/dir1/dir2/File.class")));
    System.out.println(finder.lookupOutputPath(new File("a/b/jjjjj/dir1/dir2/File.class")));
    System.out.println(finder.lookupOutputPath(new File("a/b/e/File.class")));
    System.out.println(finder.lookupOutputPath(new File("a/b/File.class")));

  }
}
