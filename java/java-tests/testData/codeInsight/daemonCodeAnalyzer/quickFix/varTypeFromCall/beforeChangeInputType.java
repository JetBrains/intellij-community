// "Change variable 'f' type to 'Function<Foo, String>'" "true"
class Test {
  enum Foo { FOO }
  public static void main(String[] args) {
    Function<String, String> f = s -> s;

    System.out.println((f).apply(<caret>Foo.FOO).toUpperCase());
  }

  interface Function<T, R> {
    R apply(T t);
  }
}