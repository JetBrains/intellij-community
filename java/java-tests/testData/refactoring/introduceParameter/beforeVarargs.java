class Test {
   public int m(int a, int... values) {
        if(a > values.length) {
           return <selection>0</selection>;
        }
        else {
           return 0;
        }
   }
}

class Test1 {
  Test t;

  public int n(int v) {
    return t.m(v, 1);
  }
}