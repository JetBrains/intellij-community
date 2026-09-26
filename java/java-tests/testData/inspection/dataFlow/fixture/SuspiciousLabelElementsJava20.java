public class Test {
  void testDominatedPatterns(Object obj) {
    switch (obj) {
      case Number i when <warning descr="Condition is always true">true</warning> -> System.out.println("A number");
      case <error descr="Label is dominated by a preceding case label 'Number i'">Integer i</error> -> System.out.println("An integer");
      default -> {}
    }
  }

  void testUnconditionalPatternAndDefaultLabel(String  obj) {
    switch (obj) {
      case <error descr="'switch' has both an unconditional pattern and a default label">String s</error> -> System.out.println("String");
      case null, <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> System.out.println("null or default");
    }
  }
}
