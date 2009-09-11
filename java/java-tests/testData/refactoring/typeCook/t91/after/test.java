class List<T> {
  T t;
}

class Test{
  class Super {
     void f (List x){
     }
  }

  class Middle extends Super {
    void f (List x){
       g(x);
       x.t = "";
    }

    void g (List x){
     x.t = "";
   }
  }

  class Sub extends Middle{
     void g (List x){
    }
  }
}