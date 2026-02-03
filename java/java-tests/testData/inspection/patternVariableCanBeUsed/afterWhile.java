// "Replace 'n' with pattern variable" "true"
class X {
  void test(Object obj) {
    while (obj instanceof Node n) {
        obj = n.getNext();
    }
  }
  
  interface Node {
    Object getNext();
  }
}