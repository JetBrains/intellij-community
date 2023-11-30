class Test {
  public boolean equals(Object o){
    return true;
  }

  boolean foo(){
    return !equals("Test");
  }
}