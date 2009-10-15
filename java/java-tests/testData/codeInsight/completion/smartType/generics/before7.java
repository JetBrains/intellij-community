class List<T> {}

class C {
  void foo () {
    List<? extends String> l = new <caret>
  }
}