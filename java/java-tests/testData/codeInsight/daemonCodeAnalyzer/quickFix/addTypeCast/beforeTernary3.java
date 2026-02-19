// "Cast expression to 'byte'" "true-preview"
class A {
 void f(boolean v) {
   int i = 10;
   Byte b = <caret> v ? 100 : i;
 }
}