import pkg.Bar;

import java.util.ArrayList;

class Goo {

  public void foo() {
    new Bar<String>(new ArrayList<String>(<caret>)) {}
  }

}