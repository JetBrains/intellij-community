import java.util.*;

class Test {
  interface A {
    Iterable<Integer> m(List ls);
  }

  interface B {
    Iterable<String> m(List l);
  }

  <error descr="'m(List)' in 'Test.B' clashes with 'm(List)' in 'Test.A'; methods have unrelated return types">interface AB extends A, B</error> {}
}
