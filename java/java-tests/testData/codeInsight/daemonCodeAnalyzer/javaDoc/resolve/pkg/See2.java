package pkg;

import java.util.List;

/**
 * @see #method(java.util.List)
 * @see #method(java.util.List<String>)
 * @see #<error descr="Cannot resolve symbol 'method(java.util.List<T>)'">method</error>(java.util.List<T>)
 * @see #<error descr="Cannot resolve symbol 'method(java.util.List<Number>)'">method</error>(java.util.List<Number>)
 */
class A2<T> {
  public void method(List<String> list) { }
}
