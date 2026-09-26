import java.util.List;

class Test {
  void variableAssignment() {
    List<?> list;
    m(list = null);
    m((list = null));
  }

  void arrayAssignment(List<?>[] arr) {
    m(arr[0] = null);
  }

  void boundedWildcards() {
    List<? extends Number> ext;
    m(ext = null);
    List<? super Number> sup;
    m(sup = null);
  }

  void distinctCaptures() {
    List<?> a;
    List<?> b;
    n(a = null, <error descr="'n(java.util.List<capture<?>>, java.util.List<capture<?>>)' in 'Test' cannot be applied to '(java.util.List<capture<?>>, java.util.List<capture<?>>)'">b = null</error>);
  }

  private static <T> void m(List<T> list) { }
  private static <T> void n(List<T> l1, List<T> l2) { }
}
