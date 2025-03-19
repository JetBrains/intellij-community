
import java.util.List;

class Foo<Bazz> {

  public void test(Foo parent) {
    Foo foo = getElements(parent).<error descr="Incompatible types. Found: 'java.lang.Object', required: 'Foo'">get</error>(0);
  }

  public static <E extends Foo<E>> List<E> getElements(E parent) {
    return null;
  }
}
