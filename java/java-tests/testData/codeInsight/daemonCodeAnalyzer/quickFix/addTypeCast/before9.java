// "Cast to 'java.lang.String'" "true"
class a {
 void f() {
   Object y = null;
   String s = <caret>(Integer) y;
 }
}
