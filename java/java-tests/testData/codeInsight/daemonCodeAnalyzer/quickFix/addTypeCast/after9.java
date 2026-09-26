// "Cast expression to 'java.lang.String'" "true-preview"
class a {
 void f() {
   Object y = null;
   String s = <caret>(String) y;
 }
}
