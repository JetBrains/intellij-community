// "Create method 'fooBar'" "true"
class FooBar {
  {
    Comparator<String> c = this::fooBar;
  }

    private int fooBar(String s, String s1) {
        return 0;
    }
}


interface Comparator<T> {
  int compare(T o1, T o2);
}