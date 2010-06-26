// "Make 'a.f' return 'int'" "false"
class a {
 private void f() {
   return ;
 }
}

class b extends a {
  <caret>int f() {
    return 0;
  }
}