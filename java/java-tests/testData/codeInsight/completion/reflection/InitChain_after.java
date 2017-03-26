class Main {
  void foo() {
    Class<?> a = Class.forName("Test");
    Class<?> b = a;
    Class<?> c = b;
    Class<?> d = c;
    Class<?> e = d;
    Class<?> f = e;
    Class<?> g = f;
    Class<?> h = g;
    Class<?> i = h;
    Class<?> j = i;
    Class<?> k = j;
    k.getField("num2");
  }
}

class Test {
  public int num;
  public int num2;
}
