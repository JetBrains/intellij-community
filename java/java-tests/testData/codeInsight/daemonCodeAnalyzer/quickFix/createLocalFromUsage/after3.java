// "Create local variable 'a'" "true-preview"
class C {
  void foo() {
      int a<caret> = 10;
      a++;
   }
}
