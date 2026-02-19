package foo;

class X {
  Object m(String s) { return null; }
}
class Y extends X {
}
class Z extends Y {
  String m(String s) { return null; }
}
class YY extends X {
  Object m(String s) { return null; }
}
class YYY extends X {
  String m(Object s) { return null; }
}