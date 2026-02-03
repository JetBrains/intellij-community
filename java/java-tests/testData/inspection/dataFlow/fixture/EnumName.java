enum X {
  FOO, BAR, BAZ
}

class C {
  void test(X x) {
    if (x == X.FOO) {
      if (<warning descr="Condition 'x.name().equals(\"FOO\")' is always 'true'">x.name().equals("FOO")</warning>) {
        
      }
    }
  }
}