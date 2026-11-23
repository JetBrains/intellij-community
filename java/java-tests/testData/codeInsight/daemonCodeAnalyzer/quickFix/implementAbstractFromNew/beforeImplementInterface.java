// "Implement methods" "true"
class c {
 void foo() {
   new I<String>()<caret>
 }
}
interface I<T> {
  void foo(T t);
}