// "Cast expression to 'int'" "true-preview"
class a {
 void f() {
   double d = 4;
   switch (<caret>(int) d) {
   }
 }
}
