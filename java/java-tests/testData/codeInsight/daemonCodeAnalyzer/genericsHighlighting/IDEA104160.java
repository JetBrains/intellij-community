import java.util.*;
class Test<T> {
  public static void foo(<error descr="'Test.this' cannot be referenced from a static context">T</error> t) {}
  public void bar(T t) {}
  
  static class A extends ArrayList<<error descr="'Test.this' cannot be referenced from a static context">T</error>> {
      static void boo(<error descr="'Test.this' cannot be referenced from a static context">T</error> t){}
  }

  class B extends ArrayList<T> {
      void foo(T r){}
  }
  
  static class C extends Test<<error descr="'Test.this' cannot be referenced from a static context">T</error>> {}
  static class D extends Test {
    <error descr="'Test.this' cannot be referenced from a static context">T</error> t;
  }
}
