// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.internal;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

public final class DiffFiles {
  public static void main(String[] args) throws Exception {
    String file1 = "/Users/max/IDEA/community/plugins/ant/src/com/intellij/lang/ant/config/impl/configuration/usedIcons.txt";
    String file2 = "/Users/max/IDEA/community/plugins/ant/src/com/intellij/lang/ant/config/impl/configuration/allIcons.txt";

    Set<String> used = load(file1);
    Set<String> all = load(file2);

    Set<String> missing = new HashSet<>(used);
    missing.removeAll(all);

    Set<String> unused = new HashSet<>(all);
    unused.removeAll(used);

    System.out.println("Missing:");
    printOrdered(missing);

    System.out.println();

    System.out.println("Unused:");
    printOrdered(unused);
  }

  private static void printOrdered(Set<String> set) {
    List<String> ordered = new ArrayList<>(set);

    Collections.sort(ordered);
    for (String item : ordered) {
      System.out.println(item);
    }
  }

  private static Set<String> load(String file) throws Exception {
    Set<String> answer = new HashSet<>();
    BufferedReader reader = new BufferedReader(new FileReader(file));
    String line;
    do {
      line = reader.readLine();
      if (line == null) break;
      if (line.isEmpty()) continue;

      answer.add(line);
    }
    while (true);

    return answer;
  }
}
