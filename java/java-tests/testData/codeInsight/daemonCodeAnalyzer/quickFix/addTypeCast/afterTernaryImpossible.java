// "Cast expression to 'B'" "true-preview"
class A {
 void f(B b) {
   B s = <caret>(B) (b == null ? this : this);
 }
}
class B extends A {}