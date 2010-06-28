// "Cast to 'float'" "true"
class a {
 float f() {
   double d = 4;
   return <caret>(float) d;
 }
}
