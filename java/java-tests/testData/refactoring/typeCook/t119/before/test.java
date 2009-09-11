class List<T> {
  <A extends T> List(List<A> x){
  }

  T t;
}

class Test {
  void foo (){
    List x = null;
    List y = new List(x);

    x.t = "";
  }
}
