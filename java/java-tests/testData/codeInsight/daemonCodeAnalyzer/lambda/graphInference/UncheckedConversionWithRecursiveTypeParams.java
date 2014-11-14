
import java.util.List;

class Foo<Bazz> {

  public void test(Foo parent) {
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'Foo'">Foo foo = getElements(parent).get(0);</error>
  }

  public static <E extends Foo<E>> List<E> getElements(E parent) {
    return null;
  }
}
