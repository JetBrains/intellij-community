class Test {
    public int m(int a, int b) {
        return m(a, b, 0);
    }

    public int m(int a, int b, int anObject) {
        if(a > b) {
           return anObject;
        }
        else {
           return anObject;
        }
    }
}

class Test1 {
  Test t;

  public int n(int v) {
    return t.m(v, 1);
  }
}