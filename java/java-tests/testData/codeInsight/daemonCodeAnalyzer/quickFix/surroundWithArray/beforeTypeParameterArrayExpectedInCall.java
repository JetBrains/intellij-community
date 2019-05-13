// "Surround with array initialization" "false"
class A<T> {

  public void test(T[] t) {
  }
  
  void foo(T t){
    test(<caret>t);
  }
}