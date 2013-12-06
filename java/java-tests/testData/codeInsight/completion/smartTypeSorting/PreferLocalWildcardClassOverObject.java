class Foo {

  static void bar(Class<?> type) {

  }

  public static void main(String[] args) {
    Class<?> type;
    bar(<caret>);
  }
}