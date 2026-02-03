import java.util.List;

class Test {
  <T> List<T> test(final List<Object> foo) {
    return (List<T>) foo;
  }

  <T> List<List<T>> test1(final List<List<Object>> foo) {
    return <error descr="Inconvertible types; cannot cast 'java.util.List<java.util.List<java.lang.Object>>' to 'java.util.List<java.util.List<T>>'">(List<List<T>>) foo</error>;
  }

  <T> List<List<List<T>>> test2(final List<List<List<Object>>> foo) {
    return <error descr="Inconvertible types; cannot cast 'java.util.List<java.util.List<java.util.List<java.lang.Object>>>' to 'java.util.List<java.util.List<java.util.List<T>>>'">(List<List<List<T>>>) foo</error>;
  }
}