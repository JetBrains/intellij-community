package ru.compscicenter.edide;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;

/**
 * author: liana
 * data: 7/15/14.
 */
public class StudyUtils {
  public static void closeSilently(Closeable stream) {
    if (stream!= null) {
      try {
        stream.close();
      }
      catch (IOException e) {
        // close silently
      }
    }
  }

  public static boolean isZip(String fileName) {
    return !fileName.contains(".zip");
  }

  public static <T> T getFirst(Iterable<T> container) {
    return container.iterator().next();
  }
}
