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
      case <error descr="Duplicate total pattern">Object oo</error>:
        break;
      case <error descr="Duplicate total pattern">Object oo && true</error>:
        break;
    }
  }

  void testDominatedPatterns(Object o) {
    switch (o) {
      case String ss && true:
        break;
      case <error descr="Label is dominated by a preceding case label 'String ss && true'">String ss</error>:
        break;
      case default:
        break;
    }
  }
}
