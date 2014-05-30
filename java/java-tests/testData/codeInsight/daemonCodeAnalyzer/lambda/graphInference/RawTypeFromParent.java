import java.util.ArrayList;
import java.util.List;

class Foo<T> {

  public void test(Foo parent) {
    List<Foo> elements = getElements(parent);


    for (<error descr="Incompatible types. Found: 'java.lang.Object', required: 'Foo'">Foo foo : getElements(parent)</error>) {
      System.out.println(foo);
    }
  }

  public static <E extends Foo<E>> List<E> getElements(E parent) {
    return new ArrayList<>();
  }
}
