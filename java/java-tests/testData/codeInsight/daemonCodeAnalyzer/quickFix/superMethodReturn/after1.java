// "Make 'f' return 'void'" "true"
class a {
 void f() {
   return ;
 }
}

class b extends a {
  <caret>void f() {
    return 0;
  }
}