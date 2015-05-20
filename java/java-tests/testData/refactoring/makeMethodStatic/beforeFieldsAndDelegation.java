class C {
  int i;
  private void fo<caret>o() {
    if (true) {
      foo();
    }
    System.out.println(i);
  }
  
  {
    foo();
  }
}