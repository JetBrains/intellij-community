// "Sort content" "true-preview"

import java.util.Arrays;
import java.util.List;

class C {
  List<String> foo() {
    return Arrays.asList//a
            //b
                    ("ReSharper OSX.xml", "ReSharper.xml", "Xcode.xml");
  }
}