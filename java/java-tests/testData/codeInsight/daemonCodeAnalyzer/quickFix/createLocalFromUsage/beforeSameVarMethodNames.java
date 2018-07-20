// "Create local variable 'a'" "true"
class C {
  C builder() {
    return this;
  }
  
  void foo() {
      builder().a(<caret>a).intValue();
   }
   
   Integer a(String s) {}
}
