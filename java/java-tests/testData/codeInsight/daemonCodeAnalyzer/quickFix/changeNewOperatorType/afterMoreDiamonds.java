// "Change 'new Object()' to 'new ArrayList<String>()'" "true-preview"

import java.util.ArrayList;

class X {

  void x() {
    ArrayList<String> x = new ArrayList<>();
  }
}