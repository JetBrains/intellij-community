public class Test {
  class A{}
  class B extends A{}

  class List<X> {
    List(X x){

    }
  }

  interface I {
    List f ();
  }

  I i1 = new I(){
    public List f(){
      return i2.f();
    }
  };

  I i2 = new I(){
    public List f(){
      return new List(new A());
    }
  };

  I i3 = new I(){
    public List f(){
      return new List(new B());
    }
  };
}
