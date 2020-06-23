import java.util.function.Function;

class Main {
  public static void main(String[] args) {
    Test test = new Test();
    ((Function<String, Long>)(args.length == 2 ? (<error descr="Method reference expression is not expected here">test::foo</error>) : <error descr="Method reference expression is not expected here">test::bar</error>)).apply("");
    
    String s = ((<warning descr="Casting '(test != null)' to 'boolean' is redundant">boolean</warning>) (test != null)) ? "a" : "b";
  }

  static class Test {
    public long foo(String s) {
      return 0;
    }

    public long bar(String s) {
      return 0;
    }
  }
}