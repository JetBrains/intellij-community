// "Replace 'n' with pattern variable" "true"
class X {
  void test(Object obj) {
    while (obj instanceof Node) {
      Node <caret>n = (Node) obj;
      obj = n.getNext();
    }
  }
  
  interface Node {
    Object getNext();
  }
}