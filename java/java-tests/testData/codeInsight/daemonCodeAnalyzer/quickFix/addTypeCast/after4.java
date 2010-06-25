// "Cast to 'int'" "true"
class a {
 void f() {
   int i;
   <caret>i = (int) 3.4f;
 }
}
