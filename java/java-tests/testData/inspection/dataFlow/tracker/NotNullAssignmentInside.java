/*
Value is always true ((s = new Object()) != null)
  's' was assigned (new Object())
    Expression cannot be null as it's newly created object (new Object())
 */

class Test {
  void test() {
    Object s;
    if (<selection>(s = new Object()) != null</selection>) {}
  }
}