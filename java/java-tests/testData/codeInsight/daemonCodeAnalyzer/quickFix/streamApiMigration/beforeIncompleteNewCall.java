// "Replace with forEach" "INFORMATION"

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

class A {
  public void foo(final Set<String> strings) throws FileNotFoundException {
    for (String s : str<caret>ings) {
      new FileInputStream();
    }

  }

}