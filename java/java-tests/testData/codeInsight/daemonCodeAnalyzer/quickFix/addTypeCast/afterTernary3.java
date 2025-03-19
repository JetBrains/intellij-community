// "Cast expression to 'byte'" "true-preview"
class A {
 void f(boolean v) {
   int i = 10;
   Byte b = v ? 100 : (byte) i;
 }
}