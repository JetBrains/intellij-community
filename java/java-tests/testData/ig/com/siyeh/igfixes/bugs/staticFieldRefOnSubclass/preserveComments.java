interface Super {
  String FOO = "";
}

class Child implements Super {}

class Bar {
  {
    String s = Child./*some comment*/FO<caret>O;
  }
}