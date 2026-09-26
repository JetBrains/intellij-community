public class DuplicateLabels {
  void testDuplicateLabels(String s) {
    switch (s) {
      case <error descr="Duplicate label 'foo'">"foo"</error>, <error descr="Duplicate label 'null'">null</error>:
        System.out.println("A");
        break;
      case <error descr="Duplicate label 'foo'">"foo"</error>:
        System.out.println("B");
        break;
      case <error descr="Duplicate label 'null'">null</error>:
        System.out.println("C");
    }
  }

  void testDuplicatePatterns(String s) {
    switch (s) {
      case "abc":
        break;
      case <error descr="Duplicate unconditional pattern">Object oo</error>:
        break;
      case <error descr="Duplicate unconditional pattern">Object oo</error> when <warning descr="Condition is always true">true</warning>:
        break;
    }
  }

  void testDominatedPatterns(Object o) {
    switch (o) {
      case String ss when <warning descr="Condition is always true">true</warning>:
        break;
      case <error descr="Label is dominated by a preceding case label 'String ss'">String ss</error>:
        break;
      default:
        break;
    }
  }
}
