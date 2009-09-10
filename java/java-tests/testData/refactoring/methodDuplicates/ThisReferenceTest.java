class ThisReferenceTest {
  void foo(){}

  void <caret>bar(){this.foo();}

  static void main(ThisReferenceTest t){
    t.foo();
  }
}