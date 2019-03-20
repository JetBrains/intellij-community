public class Test {
  void foo(Object o) {
    if (o instanceof A) {
      ((A)o).bar();
    }
  }//ins and outs
//in: PsiParameter:o
//exit: SEQUENTIAL PsiMethod:foo
}

class A {
  void bar(){}
}