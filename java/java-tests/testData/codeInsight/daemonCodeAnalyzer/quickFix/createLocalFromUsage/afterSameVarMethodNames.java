// "Create local variable 'a'" "true"
class C {
  C builder() {
    return this;
  }
  
  void foo() {
      String a;
      builder().a(a).intValue();
   }
   
   Integer a(String s) {}
}
