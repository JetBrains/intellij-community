// "Cast to 'B'" "true"
class A {
 void f(B b) {
   B s = <caret>b == null ? null : (B) this;
 }
}
class B extends A {}