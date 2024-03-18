// "Change field 'f' type to 'Function<Foo, Object>'" "false"
class Test {
  enum Foo { FOO }
  public static void main(String[] args) {
    Function<String, String> f = s -> s;

    // does not compile because apply expects a string
    System.out.println(f.apply(<caret>Foo.FOO).toUpperCase());
  }
}