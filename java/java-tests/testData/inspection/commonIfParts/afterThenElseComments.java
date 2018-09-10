// "Extract common part removing branch " "true"

import java.io.*;

class F {
  boolean x(File file) {
      // quick fail
      if (file.getPath().contains("/test/")) {
      System.out.println();
      }
      return false;
  }
}