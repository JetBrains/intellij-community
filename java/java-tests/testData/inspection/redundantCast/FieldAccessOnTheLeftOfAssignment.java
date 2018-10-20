abstract class Foo {
  protected int field;

  public int f(Bar t){
    ((<warning descr="Casting 't' to 'Foo' is redundant">Foo</warning>)t).field = 0;
    return ((<warning descr="Casting 't' to 'Foo' is redundant">Foo</warning>)t).field;
  }

}

class Bar extends Foo{}