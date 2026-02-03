class Foo {

}

class Bar {
  Foo getFoo() {}
}

class Main {
  {
    Bar localBar = bar;
    Foo f = <caret>
  }

  Bar bar;
  Bar getBar() { return bar; }
  Bar findBar() { return getBar(); }


}