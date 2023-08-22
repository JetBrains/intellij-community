// "Split into separate declarations" "true"
class MutualDependentForDeclarations {

  void f() {
      int a = 0;/*1*//*2*//*3*//*4*//*5*//*6*/
      for(int[] b =<caret> {a}; ;) {}
  }
}