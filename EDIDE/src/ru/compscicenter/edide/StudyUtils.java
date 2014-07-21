package ru.compscicenter.edide;

import java.io.*;

/**
 * author: liana
 * data: 7/15/14.
 */
public class StudyUtils {
  public static void closeSilently(Closeable stream) {
    if (stream != null) {
      try {
        stream.close();
      }
      catch (IOException e) {
        // close silently
      }
    }
  }

  public static boolean isZip(String fileName) {
    return fileName.contains(".zip");
  }

  public static <T> T getFirst(Iterable<T> container) {
    return container.iterator().next();
  }

  public static String getFileText(String parentDir, String fileName, boolean wrapHTML) {
    File inputFile = new File(parentDir, fileName);
    StringBuilder taskText = new StringBuilder();
    if (wrapHTML) {
      taskText.append("<html>");
    }
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile)));
      String line;
      while ((line = reader.readLine()) != null) {
        taskText.append(line);
        if (wrapHTML) {
          taskText.append("<br>");
        }
      }
      if (wrapHTML) {
        taskText.append("</html>");
      }
      return taskText.toString();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    finally {
      StudyUtils.closeSilently(reader);
    }
    return null;
  }
}
