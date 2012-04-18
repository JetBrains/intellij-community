abstract class Foo {
  protected int field;

  public int f(Bar t){
    ((Foo)t).field = 0;
    return ((Foo)t).field;
  }

}

class Bar extends Foo{}