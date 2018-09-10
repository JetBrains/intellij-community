// "Cast parameter to 'java.lang.Integer'" "true"
class a {
   void m(Integer i){}
   void m(String s) {}
   void f() {
       m((Integer) null);
   }
}
