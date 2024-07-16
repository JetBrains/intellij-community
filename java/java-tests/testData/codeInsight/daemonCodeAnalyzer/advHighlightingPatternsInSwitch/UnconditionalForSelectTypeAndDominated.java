package dfa;

public class UnconditionalForSelectTypeAndDominated {
  interface AA1{}
  interface AA2{}
  interface AA extends AA1, AA2{}
  interface B {}
  private static void testAA(AA aa){
    switch (aa) {
      case AA1 aa1 -> System.out.println(1);
      case <error descr="Label is dominated by a preceding case label 'AA1 aa1'">B aa1</error> -> System.out.println(1);
    }
  }
}
