public class Test {
  void testDominatedPatterns(Object obj) {
    switch (obj) {
      case Number i when true -> System.out.println("A number");
      case <error descr="Label is dominated by a preceding case label 'Number i when true'">Integer i</error> -> System.out.println("An integer");
      default -> {}
    }
  }
}
