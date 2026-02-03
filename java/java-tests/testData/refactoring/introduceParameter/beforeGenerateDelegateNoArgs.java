class Test {
    public int m() {
        return <selection>0</selection>;
    }
}

class Test1 {
  Test t;

  public int n(int v) {
    return t.m();
  }
}