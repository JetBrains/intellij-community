interface Base {
  Base[] EMPTY_ARRAY = new Base[0];
}

interface Derived1 extends Base {}

interface Derived2 extends Base {
  Derived2[] EMPTY_ARRAY = new Derived2[0];
}

class Implementor implements Derived1, Derived2 {
  //Implementor[] EMPTY_ARRAY = new Implementor[0];
  Derived2[] f()  {
    return <ref>EMPTY_ARRAY;  //conflict
  }
}
