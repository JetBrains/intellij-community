class ExposeAnotherProblem {

  class OneClass<T> {
    public T get(){
      return null;
    }
  }

  class AnotherClass<T> {}


  static <T, R extends OneClass<T>> R method1(AnotherClass<T> param) {
    return null;
  }

  <E> E method2(AnotherClass<E> param){
    return method1(param).get();
  }

}