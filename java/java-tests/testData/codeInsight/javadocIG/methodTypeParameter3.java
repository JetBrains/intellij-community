class MethodTypeParam3 {
  /**
  * @param <T> type param
  */
  <<caret>T extends Object & Comparable<? super T>> void foo(T t) {
  }
}