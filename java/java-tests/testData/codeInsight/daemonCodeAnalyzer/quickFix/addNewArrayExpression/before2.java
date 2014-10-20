// "Add 'new <lambda expression>[]{}'" "false"
class c {
 void f() {
   v({() -> {}});
 }
  void v(Runnable[] rs){}
}