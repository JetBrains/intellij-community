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
}
