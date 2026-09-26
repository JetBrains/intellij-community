// "Change 'new C()' to 'new C()'" "false"

class X {

  C x() {
    class C {}
    return new<caret> C();
  }
}
