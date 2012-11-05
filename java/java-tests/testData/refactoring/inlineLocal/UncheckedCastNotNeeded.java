import java.util.*;

class X {
  List getChildren() {
    return null;
  }

  void iterate() {
    List<X> xs = getChildren();
    foo(x<caret>s);
  }
  void foo(List<X> l){}
}