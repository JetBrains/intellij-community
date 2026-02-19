// "Cast expression to 'B'" "true-preview"
class A {
 void f(B b) {
   B s =b == null ? null : (B) <caret>this;
 }
}
class B extends A {}