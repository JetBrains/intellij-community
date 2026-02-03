import java.util.List;

class A<T> {
  T t;
  List<T> list = new List<T>();
}

class B extends A<S<caret>tring> {
  void foo() {
    if (t == null) return;
    if (list == null) return;
    for (String s : list) {
      //do nothing
    }
  }
}