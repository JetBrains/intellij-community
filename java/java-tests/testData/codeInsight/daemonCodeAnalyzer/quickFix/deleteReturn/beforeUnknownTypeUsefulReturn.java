// "Delete return value 'null'" "true"

class Test {

  void foo(boolean b) {
    if (b) return<caret> null;
    System.out.println("bar");
  }
}