class Foo {

}

class FooEx extends Foo {

}

class FooExEx extends FooEx {

  public void m(FooExEx f) {
    System.out.println(f + " <--- f value");
  }

}