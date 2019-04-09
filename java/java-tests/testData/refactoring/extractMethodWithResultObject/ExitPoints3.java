class Test{
  void foo(boolean cond1, boolean cond2, boolean cond3) {
    if (cond1){      
      <selection>if (cond2) return;
      x();</selection>
    }
    else if (cond3){
    }
  }
  void x() {}
}
