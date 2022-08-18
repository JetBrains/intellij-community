// "Cast argument to 'Integer'" "true-preview"
class a {
   void m(Integer i){}
   void m(String s) {}
   void f() {
       m(<caret>null);
   }
}
