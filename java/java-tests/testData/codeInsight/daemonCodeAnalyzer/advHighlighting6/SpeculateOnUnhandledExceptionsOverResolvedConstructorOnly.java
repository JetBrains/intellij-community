class Base {

  String[] items;

  public Base(String ... items) throws Exception {
    this.items = items;
  }

  public Base() {
    this.items = null;
  }
}

class Derived extends Base {
  public Derived() {}
}
class Derived1 extends Base {}