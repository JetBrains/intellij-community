// "Cast to 'char'" "true"
class a {
 void f() {
   double d = 4;
   switch ('c') {
     case <caret>(char) 3.3:
   }
 }
}
