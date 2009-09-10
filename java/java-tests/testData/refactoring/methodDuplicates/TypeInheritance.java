class Test {
  class I {}
  class I1 extends I {}

  void <caret>foo(I i){
    System.out.println(i);
  }

  void bar(I1 i){
    System.out.println(i);
  }

}