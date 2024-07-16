// "Convert canonical constructor to compact form" "false" 
record Rec() {
  Rec(<caret>) throws IllegalArgumentException {
    System.out.println("hello");
  }
}
