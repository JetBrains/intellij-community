// "Make 'f' return 'void'" "true"
class a {
 void f() {
   return ;
 }
}

class b extends a {
  <caret>int f() {
    return 0;
  }
}