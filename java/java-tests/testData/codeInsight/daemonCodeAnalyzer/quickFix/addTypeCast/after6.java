// "Cast to 'int'" "true"
class a {
 void f() {
   double d = 4;
   switch (<caret>(int) d) {
   }
 }
}
