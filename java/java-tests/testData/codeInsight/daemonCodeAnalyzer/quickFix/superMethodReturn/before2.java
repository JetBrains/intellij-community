// "Make 'a.f()' return 'int'" "true-preview"
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