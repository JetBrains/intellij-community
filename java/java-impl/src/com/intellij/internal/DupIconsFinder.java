// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.internal;

import com.intellij.util.containers.MultiMap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

public final class DupIconsFinder {

  private static final MultiMap<Integer, String> hashes = new MultiMap<>();
  private static int totalClusters;
  private static int totalDups;

  public static void main(String[] args) throws Exception {
    File root = new File("/Users/max/images/icons");

    processDir(root);

 /*
    Set<String> allIcons = new HashSet<String>();
    ArrayList<String> sorted = new ArrayList<String>(allIcons);
    Collections.sort(sorted);

    for (String icon : sorted) {
      System.out.println(icon);
    }
*/
    for (Map.Entry<Integer, Collection<String>> entry : hashes.entrySet()) {
      printDups(entry.getValue());
    }

    System.out.println("Total: " + totalDups + " files duplicated in " + totalClusters + " clusters");
  }

  private static void processDir(File root) throws Exception {
    File[] files = root.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && file.getName().endsWith(".png")) {
          processFile(file.getPath());
        }
        else if (file.isDirectory()) {
          processDir(file);
        }
      }
    }
  }

  private static void printDups(Collection<String> files) {
    if (files.size() > 1) {
      System.out.println("Duplicate " + files.size() + " files");
      for (String file : files) {
        System.out.println(file);
        totalDups++;
      }
      totalClusters++;
      System.out.println();
    }
  }

  private static void processFile(String path) throws Exception {
    try (InputStream stream = new BufferedInputStream(new FileInputStream(path))) {
      int hc = 0;
      while (true) {
        int b = stream.read();
        if (b == -1) break;
        hc = hc * 31 + b;
      }

      hashes.putValue(hc, path);
    }
  }
}
