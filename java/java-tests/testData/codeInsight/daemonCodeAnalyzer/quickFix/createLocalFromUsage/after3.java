// "Create local variable 'a'" "true"
class C {
  void foo() {
      int a<caret> = 10;
      a++;
   }
}
