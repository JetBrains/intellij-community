// "Create method 'fooBar'" "true"
class FooBar {
  {
    Comparator<String> c = this::foo<caret>Bar;
  }
}


interface Comparator<T> {
  int compare(T o1, T o2);
}