// "Make 'a.f()' return 'int'" "true-preview"
class a {
 int f() {
     return <selection><caret>0</selection>;
 }
}

class b extends a {
  int f() {
    return 0;
  }
}