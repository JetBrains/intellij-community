class MyTest<T> {
  static void m(Ref<? super String> commentRef) {
    commentRef = coalesce(commentRef, commentRef);
  }

  static <T> T coalesce(T t1, T t2) {
    return t1;
  }

  static class Ref<T> { }
}
