// "Cast to 'B'" "true"
class A {
 void f(B b) {
   B s = <caret>b == null ? null : this;
 }
}
class B extends A {}