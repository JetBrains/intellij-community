// "Replace 'n' with pattern variable" "true"
class X {
  void test(Object obj) {
    while (!(obj instanceof Node)) {
      obj = getNext();
    }
    Node <caret>n = (Node)obj;
    System.out.println(n);
  }
  
  native Object getNext();
  
  interface Node {}
}