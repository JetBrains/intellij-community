// "Surround with array initialization" "true"
class A<T> {

  public void test(Object[] t) {
  }
  
  void foo(T t){
    test(new Object[]{t});
  }
}