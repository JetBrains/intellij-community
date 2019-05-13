// "Create local variable 'a'" "true"
class C {
  void foo() {
      <caret>a = 10;
      a++;
   }
}
