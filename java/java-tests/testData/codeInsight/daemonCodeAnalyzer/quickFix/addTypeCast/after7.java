// "Cast expression to 'char'" "true-preview"
class a {
 void f() {
   double d = 4;
   switch ('c') {
     case <caret>(char) 3.3:
   }
 }
}
