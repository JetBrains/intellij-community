class Test {
   public int m(int a, int anObject, int... values) {
        if(a > values.length) {
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
    return t.m(v, 0, 1);
  }
}