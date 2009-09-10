class Test {
  class I {}
  class I1 extends I {}

  void foo(I i){
    System.out.println(i);
  }

  void <caret>bar(I1 i){
    System.out.println(i);
  }

}