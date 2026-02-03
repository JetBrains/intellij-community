class Test {
  
  <fold text='//...'>// class-level folding1
  // class-level folding2</fold>
  
  public void foo() <fold text='{...}'>{
    <fold text='//...'>// method-level folding1
    // method-level folding2</fold>
    int i = 1;
    // method-level folding2
  }</fold>

  // class-level folding3
}