public class Usage<T extends Usage<T>> {
  void foo(T t) {
    "".<caret>
  }

  void someMethod() {}

}
