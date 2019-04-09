class Test{
  public void foo(boolean cond1, boolean cond2, boolean cond3) {
    if (cond1){
      <selection>if (cond2) return;</selection>
    }
    else if (cond3){
    }
  }
}
