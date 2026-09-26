// "Cast expression to 'B'" "true-preview"
class A {
 void f(B b) {
   B s = b == null ? <caret>(B) this : b;
 }
}
class B extends A {}