class A<T> {}
class Bbbb<T> extends A<T> {}
public class C {
  void m(A a) {
    if (a instanceof B<caret>) {

    }
  }
}