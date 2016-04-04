import java.util.*;

class Test {


  void m(Runnable p) { }
  void m(List<Runnable> p) { }

  {
    m(foo());
    m<error descr="Ambiguous method call: both 'Test.m(Runnable)' and 'Test.m(List<Runnable>)' match">(bar())</error>;
  }

  <T> List<T> foo() {
    return null;
  }

  <T> T bar() {
    return null;
  }


}
