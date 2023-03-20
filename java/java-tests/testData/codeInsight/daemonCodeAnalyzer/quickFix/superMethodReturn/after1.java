// "Make 'f()' return 'void'" "true-preview"
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