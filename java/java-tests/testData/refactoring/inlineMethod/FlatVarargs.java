class Varargs {
  void xxx() {
    foo(new String[] {"aa", "hh"});
  }

  void <caret>foo(String... ss) {
    bar(ss);
  }

  void bar(String s, String ss){

  }

  void bar(String... ss) {
  }
}