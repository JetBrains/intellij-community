
import java.util.*;

class Test {
  public static void main(String[] args) {
    Factory factory = new Factory();
    final Class<? extends ClassB> bClass = null;
    ClassB b   = factory.create(bClass);
    String str = factory.<error descr="Incompatible types. Found: 'capture<? extends Test.ClassB>', required: 'java.lang.String'">create</error>(bClass);
  }

  public static class Factory {
    <T extends ClassA<I>, I extends List<String>> T create(Class<T> pClassA) {
      return null;
    }
  }

  interface ClassA<T extends List<String>> {}
  interface ClassB extends ClassA<ArrayList<String>> {}
}