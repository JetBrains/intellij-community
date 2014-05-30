import java.util.ArrayList;
import java.util.List;

class Foo<T> {

  public void test(Foo parent) {
    List<Foo> elements = getElements(parent);
  }

  public static <E extends Foo<E>> List<E> getElements(E parent) {
    return new ArrayList<>();
  }
}
