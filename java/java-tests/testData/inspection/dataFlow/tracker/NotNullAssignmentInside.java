/*
Value is always true ((s = new Object()) != null; line#10)
  's' was assigned (=; line#10)
    Expression cannot be null as it's newly created object (new Object(); line#10)
 */

class Test {
  void test() {
    Object s;
    if (<selection>(s = new Object()) != null</selection>) {}
  }
}