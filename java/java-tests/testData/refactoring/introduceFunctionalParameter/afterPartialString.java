import java.util.function.Supplier;

class Test {
  void foo(Supplier<String> anObject) {
      System.out.println("a" + anObject.get() + "d");
  }
}