// "Replace 'n' with pattern variable" "true"
class X {
  void test(Object obj) {
    while (!(obj instanceof Node n)) {
      obj = getNext();
    }
      System.out.println(n);
  }
  
  native Object getNext();
  
  interface Node {}
}