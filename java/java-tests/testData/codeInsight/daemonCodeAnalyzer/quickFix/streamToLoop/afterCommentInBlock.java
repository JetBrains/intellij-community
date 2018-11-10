// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.io.File;
import java.util.Arrays;

class X {

  void x(File[] files) {
      for (File s : files) {
          File dest = new File(s.getAbsolutePath());
          System.out.println("unable to rename " + s + " to " + dest); //comment
      }
  }

}