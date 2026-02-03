class AbstractSet {
  public void addX() {}
  public void addY() {}
}

class MySet1 extends AbstractSet {
  public void addX() {}
}
class MySet2 extends AbstractSet {
  public void addX() {}
}

class Foo {
  
  void foo(MySet1 set1, MySet2 set2) {
    set1.ad<caret>
  }
}