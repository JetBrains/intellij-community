// "Cast expression to 'int'" "true-preview"
class a {
 void f() {
   int i;
   <caret>i = (int) 3.4f;
 }
}
