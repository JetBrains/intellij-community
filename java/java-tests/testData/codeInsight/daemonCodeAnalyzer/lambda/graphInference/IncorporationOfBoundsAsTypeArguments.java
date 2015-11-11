
import java.util.*;

class Test {
  public static void main(String[] args) {
    Factory factory = new Factory();
    final Class<? extends ClassB> bClass = null;
    ClassB b   = factory.create(bClass);
    String str = factory.create<error descr="'create(java.lang.Class<T>)' in 'Test.Factory' cannot be applied to '(java.lang.Class<capture<? extends Test.ClassB>>)'">(bClass)</error>;
  }

  public static class Factory {
    <T extends ClassA<I>, I extends List<String>> T create(Class<T> pClassA) {
      return null;
    }
  }

  interface ClassA<T extends List<String>> {}
  interface ClassB extends ClassA<ArrayList<String>> {}
}