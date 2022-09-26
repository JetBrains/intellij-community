// "Surround with array initialization" "true-preview"
import java.util.List;

class X {

  void x() {
    List<String> l = of(<caret>1);
  }

  static <E> List<E> of(E[] elements) {
    return null;
  }
}