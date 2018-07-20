// "Extract common part removing branch " "true"

import java.io.*;

class F {
  boolean x(File file) {
    if<caret> (file.getPath().contains("/test/")) {
      System.out.println();
      return false;
    }
    else {
      return false; // quick fail
    }
  }
}