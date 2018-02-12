
import java.util.*;

class Test {
  public static void main(String[] args) {
    Factory factory = new Factory();
    final Class<? extends ClassB> bClass = null;
    ClassB b   = factory.create(bClass);
    String str = <error descr="Incompatible types. Required String but 'create' was inferred to T:
no instance(s) of type variable(s)  exist so that capture of ? extends ClassB conforms to String">factory.create(bClass);</error>
  }

  public static class Factory {
    <T extends ClassA<I>, I extends List<String>> T create(Class<T> pClassA) {
      return null;
    }
  }

  interface ClassA<T extends List<String>> {}
  interface ClassB extends ClassA<ArrayList<String>> {}
}