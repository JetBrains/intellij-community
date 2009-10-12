/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.util.Pair;
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

    return findOutputPath(path, idx, node);
  }

  private static @Nullable String findOutputPath(final String path, int idx, Node node) {
    while (true) {
      final int slashIndex = path.indexOf('/', idx);
      final String name = slashIndex < idx? path.substring(idx) : path.substring(idx, slashIndex);
      node = node.getChild(name);
      if (node == null) {
        return null;
      }
      if (node.isOutputRoot()) {
        if (node.hasChildren() && slashIndex > idx) {
          final String candidate = findOutputPath(path, slashIndex + 1, node);
          if (candidate != null) {
            return candidate;
          }
        }
        return node.getData();
      }
      if (slashIndex < 0) {
        return null;  // end of path reached
      }
      idx = slashIndex + 1;
    }
  }

  private static class Node {
    private final String myName;
    @Nullable
    private Object myChildren; // either a Node or a Map<String, Node> or a String  or a Pair<String, Node or NodeMap>

    private Node(String name) {
      myName = name;
    }

    public String getName() {
      return myName;
    }

    public boolean isOutputRoot() {
      return myChildren instanceof String || myChildren instanceof Pair;
    }

    public boolean hasChildren() {
      return myChildren instanceof Map || myChildren instanceof Pair;
    }

    @Nullable
    public String getData() {
      if (myChildren instanceof String) {
        return (String)myChildren;
      }
      if (myChildren instanceof Pair) {
        //noinspection unchecked
        return (String)((Pair)myChildren).first;
      }
      return null;
    }

    public void setData(String path) {
      if (myChildren != null) {
        if (myChildren instanceof String) {
          myChildren = path;
        }
        else {
          myChildren = new Pair(path, myChildren instanceof Pair? ((Pair)myChildren).second : myChildren);
        }
      }
      else {
        myChildren = path;
      }
    }

    public Node addChild(String childName) {
      if (myChildren == null) {
        final Node node = new Node(childName);
        myChildren = node;
        return node;
      }
      if (myChildren instanceof String) {
        final Node node = new Node(childName);
        myChildren = new Pair(myChildren, node);
        return node;
      }

      final Map<String, Node> map;
      if (myChildren instanceof Map) {
        map = (Map<String, Node>)myChildren;
      }
      else if (myChildren instanceof Node)  {
        final Node existingChild = (Node)myChildren;
        myChildren = map = new HashMap<String, Node>();
        map.put(existingChild.getName(), existingChild);
      }
      else { // myChildren is a Pair
        Object children = ((Pair)myChildren).second;
        if (children instanceof Map) {
          map = (Map<String, Node>)children;
        }
        else {
          final Node existingChild = (Node)children;
          myChildren = new Pair(((Pair)myChildren).first, map = new HashMap<String, Node>());
          map.put(existingChild.getName(), existingChild);
        }
      }

      Node node = map.get(childName);
      if (node == null) {
        map.put(childName, node = new Node(childName));
      }
      return node;
    }

    @Nullable
    public Node getChild(String childName) {
      final Object children = myChildren instanceof Pair? ((Pair)myChildren).second : myChildren;
      if (children instanceof Node) {
        final Node childNode = (Node)children;
        return childName.equals(childNode.getName())? childNode : null;
      }
      if (children instanceof Map) {
        return ((Map<String, Node>)myChildren).get(childName);
      }
      return null;
    }
  }


  public static void main(String[] args) {
    final Set<File> set = new HashSet<File>();
    set.add(new File("/a/b/c"));
    set.add(new File("a/b/d"));
    set.add(new File("a/b/e"));
    set.add(new File("/a/b/f/g"));
    set.add(new File("/a/b/f/g/zzz"));

    final OutputPathFinder finder = new OutputPathFinder(set);

    System.out.println(finder.lookupOutputPath(new File("a/b")));
    System.out.println(finder.lookupOutputPath(new File("a/b/c/dir1/dir2/File.class")));
    System.out.println(finder.lookupOutputPath(new File("a/b/d/dir1/dir2/File.class")));
    System.out.println(finder.lookupOutputPath(new File("a/b/jjjjj/dir1/dir2/File.class")));
    System.out.println(finder.lookupOutputPath(new File("a/b/e/File.class")));
    System.out.println(finder.lookupOutputPath(new File("a/b/File.class")));

    System.out.println(finder.lookupOutputPath(new File("/a/b/f/g/File.class")));
    System.out.println(finder.lookupOutputPath(new File("/a/b/f/g/ttt/yy/File.class")));
    System.out.println(finder.lookupOutputPath(new File("/a/b/f/g/zzz/File.class")));
    System.out.println(finder.lookupOutputPath(new File("/a/b/f/g/zzz/mmm/ttt/File.class")));

  }
}
