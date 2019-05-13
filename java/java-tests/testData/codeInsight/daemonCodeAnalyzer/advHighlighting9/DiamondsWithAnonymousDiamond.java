
class Outer {
  Comparable<String> f() {
    return new Comparable<>() {
      @Override
      public int compareTo(String o) {
        return 0;
      }
    };
  }
}