interface List {
  void add();
}

class ArrayList implements List {
  public void add() {}
}

class OtherList extends ArrayList {}

class Foo {
  {
    ArrayList l = new <caret>
  }
}