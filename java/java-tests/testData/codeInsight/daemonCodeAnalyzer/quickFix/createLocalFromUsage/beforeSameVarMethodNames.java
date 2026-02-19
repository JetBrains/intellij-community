// "Create local variable 'a'" "true-preview"
class C {
  C builder() {
    return this;
  }
  
  void foo() {
      builder().a(<caret>a).intValue();
   }
   
   Integer a(String s) {}
}
