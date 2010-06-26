// "Make 'a.f' return 'int'" "true"
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