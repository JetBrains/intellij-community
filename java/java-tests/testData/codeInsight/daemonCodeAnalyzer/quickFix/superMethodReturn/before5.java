// "Make 'f' return 'void'" "false"
class a {
 void f(int i) {
   return ;
 }
}

class b extends a {
  <caret>int f() {
    return 0;
  }
}