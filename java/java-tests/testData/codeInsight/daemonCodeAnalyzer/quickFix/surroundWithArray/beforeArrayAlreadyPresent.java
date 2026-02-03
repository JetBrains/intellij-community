// "Surround with array initialization" "false"
import java.util.List;

class X {

  void x() {
    List<String> l = of(new<caret> Integer[]{1});
  }

  static <E> List<E> of(E[] elements) {
    return null;
  }
}