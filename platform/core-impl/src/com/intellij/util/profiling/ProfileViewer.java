/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.profiling;

import com.intellij.util.containers.ContainerUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Created by Max Medvedev on 30/03/15
 */
class ProfileViewer {
  private class Ref {
    private final String text;
    private final String fileName;
    private final int hashcode;

    private int totalTime;

    private final List<Occurrence> occurrences = new ArrayList<Occurrence>();

    Ref(String text, int hashcode, String filename) {
      this.text = text;
      this.hashcode = hashcode;
      this.fileName = filename;
    }

    void addOccurrence(Occurrence o) {
      occurrences.add(o);
      totalTime += o.time;
    }

    @Override
    public int hashCode() {
      return hashcode;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Ref && hashcode == ((Ref)obj).hashcode;
    }

    @Override
    public String toString() {
      return text+ " (" + totalTime + ")";
    }
  }

  class Occurrence {
    private Ref ref;
    private int time;
    private String type;

    private final List<Occurrence> subOccurrences = new ArrayList<Occurrence>();

    void setData(String line) {
      String[] split = line.split(" :: ");
      assert split.length == 5: line;
      try {
        type = split[0];
        String filename = split[1];
        String text = split[2];
        int hashcode = Integer.parseInt(split[3]);
        time = Integer.parseInt(split[4]);

        Ref ref = map.get(hashcode);
        if (ref == null) {
          ref = new Ref(text, hashcode, filename);
          map.put(hashcode, ref);
        }

        this.ref = ref;

        ref.addOccurrence(this);
      }
      catch (NumberFormatException e) {
        throw new NumberFormatException(line);
      }
    }

    Ref getRef() {
      return ref;
    }

    int getTime() {
      return time;
    }

    void addSubOccurrence(Occurrence o) {
      subOccurrences.add(o);
    }

    @Override
    public String toString() {
      return time + " (" + (ref != null ? ref.text : null) + ")";
    }
  }

  HashMap<Integer, Ref> map = new HashMap<Integer, Ref>();

  public static void main(String[] args) throws IOException {
    new ProfileViewer().run();
  }
  void run() throws IOException {
    List<String> list = new ArrayList<String>();
    File dir = new File("/users/maxmedvedev/work/resolve_info");

    for (File file : dir.listFiles()) {
      if (file.getName().endsWith(".txt")) {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        try {
          while (true) {
            String line = reader.readLine();
            if (line == null) break;
            list.add(line);
          }
        }
        finally {
          reader.close();
        }
      }
    }

    Occurrence root = new Occurrence();

    ArrayDeque<Occurrence> stack = new ArrayDeque<Occurrence>();
    stack.push(root);
    int curSpaces = 0;

    for (String line : list) {
      int spaces = leadingSpaces(line);

      if (curSpaces <= spaces) {
        while (curSpaces < spaces) {
          Occurrence occurrence = new Occurrence();
          stack.peek().addSubOccurrence(occurrence);
          stack.push(occurrence);
          curSpaces += 2;
        }

        Occurrence occurrence = new Occurrence();
        occurrence.setData(line.trim());
        stack.peek().addSubOccurrence(occurrence);
      } else {
        stack.pop().setData(line);
        curSpaces -= 2;
      }
    }


    List<Ref> refs = new ArrayList<Ref>(map.values());

    ContainerUtil.sort(refs, new Comparator<Ref>() {
      @Override
      public int compare(Ref ref1, Ref ref2) {
        return ref2.totalTime - ref1.totalTime;
      }
    });

    int var = 1;
  }

  static int leadingSpaces(String s) {
    int i = 0;
    while (i < s.length() && s.charAt(i) == ' ') i++;
    return i;
  }

}




