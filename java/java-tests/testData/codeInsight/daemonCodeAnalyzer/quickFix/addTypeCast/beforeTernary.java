// "Cast to 'B'" "true"
class A {
 void f(B b) {
   B s = <caret>b == null ? this : b;
 }
}
class B extends A {}