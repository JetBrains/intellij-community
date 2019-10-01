package foo;

class X {
  Object m(String s) {}
}
class Y extends X {
}
class Z extends Y {
  String m(String s) {}
}
class YY extends X {
  Object m(String s) {}
}
class YYY extends X {
  String m(Object s) {}
}