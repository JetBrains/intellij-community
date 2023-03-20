// "Create local variable 'a'" "true-preview"
class C {
  void foo() {
      <caret>a = 10;
      a++;
   }
}
