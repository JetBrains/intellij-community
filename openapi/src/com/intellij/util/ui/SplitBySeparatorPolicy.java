package com.intellij.util.ui;

import com.intellij.util.ui.FilePathSplittingPolicy;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * author: lesya
 */
public class SplitBySeparatorPolicy extends FilePathSplittingPolicy {
  public String getPresentableName(File file, int length) {
    String absolutePath = file.getPath();
    if (absolutePath.length() <= length) return absolutePath;
    String name = file.getName();
    if (length < name.length()) return "...";
    if (length == name.length()) return name;

    List<String> components = getComponents(file);

    int currentLength = 0;

    List<String> end = new ArrayList<String>();
    List<String> begin = new ArrayList<String>();

    int size = components.size();
    int mult = 1;
    int currentIndex = 0;
    for (int i = size - 1; i >= 0; i--) {
      String s = components.get(currentIndex);
      currentLength += s.length();
      if (currentLength > (length - 3)) break;
      if (mult > 0) {
        end.add(s);
      }
      else {
        begin.add(s);
      }
      currentIndex = currentIndex + i * mult;
      mult *= -1;
    }


    if (end.isEmpty()) {
      return name;
    }

    StringBuffer result = new StringBuffer();

    for (Iterator<String> iterator = begin.iterator(); iterator.hasNext();) {
      result.append(iterator.next());
    }

    result.append("...");

    for (int i = end.size() - 1; i >=0; i--) {
      result.append(end.get(i));
    }

    return result.toString();
  }

  private ArrayList<String> getComponents(File file) {
    ArrayList<String> result = new ArrayList<String>();
    File current = file;
    while (current != null) {
      result.add(getFileName(current));
      current = current.getParentFile();
      if (current != null) result.add(File.separator);
    }
    return result;
  }

  private String getFileName(File current) {
    String result = current.getName();
    if (result.length() > 0) return result;
    String path = current.getPath();
    return path.substring(0, path.length() - 1);
  }
}
