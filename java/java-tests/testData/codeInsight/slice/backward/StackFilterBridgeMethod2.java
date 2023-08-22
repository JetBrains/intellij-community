class MainTest {
  interface Foo<T> {
    String get(T x);
  }

  static class Bar implements Foo<String> {
    public String get(String <flown1>x) {
      System.out.println(<caret>x.trim());
      return x;
    }
  }

  public static void main(String[] args) {
    bar(new Bar());
  }

  private static void bar(Foo s) {
    s.get(<flown11>null);
  }
}