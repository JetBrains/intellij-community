class AnonymousInAnonymous {
  int num1;
  int num2;

  AnonymousInAnonymous() {}
  void foo() {
    new Object() {
       public String toString() {
         return new Object(){
           void method() {<caret>}
         }.toString();
       }
    };
  }
}