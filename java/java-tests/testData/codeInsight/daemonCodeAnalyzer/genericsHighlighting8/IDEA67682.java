import java.util.List;

abstract class B {
  <T> void foo(List<List<String>[]> x) {
      Object y1 = <error descr="Inconvertible types; cannot cast 'java.util.List<java.util.List<java.lang.String>[]>' to 'java.util.List<java.util.List<T>[]>'">(List<List<T>[]>)x</error>;
  }

  <T> void foo1(List<List<List<String>>[]> x) {
      Object y1 = <error descr="Inconvertible types; cannot cast 'java.util.List<java.util.List<java.util.List<java.lang.String>>[]>' to 'java.util.List<java.util.List<java.util.List<T>>[]>'">(List<List<List<T>>[]>)x</error>;
  }

  <T> void foo2(List<String[]> x) {
      Object y1 = <error descr="Inconvertible types; cannot cast 'java.util.List<java.lang.String[]>' to 'java.util.List<T[]>'">(List<T[]>)x</error>;
  }
}
