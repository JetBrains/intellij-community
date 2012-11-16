import java.util.*;

class X {
  List getChildren() {
    return null;
  }

  void iterate() {
    List<X> xs = getChildren();
    for (X x : x<caret>s) {}
  }
}