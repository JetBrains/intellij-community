// "Sort content" "true"

import java.util.Arrays;
import java.util.List;

class C {
  List<String> foo() {
    return Arrays.asList//a
      //b
      ("Xco<caret>de.xml", "ReSharper.xml", "ReSharper OSX.xml");
  }
}