import java.util.*;

class Test {


  void m(Runnable p) { }
  void m(List<Runnable> p) { }

  {
    m(foo());
    m<error descr="Cannot resolve method 'm(java.lang.Object)'">(bar())</error>;
  }

  <T> List<T> foo() {
    return null;
  }

  <T> T bar() {
    return null;
  }


}
