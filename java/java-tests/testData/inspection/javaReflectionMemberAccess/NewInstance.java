import java.lang.reflect.Array;

class Vararg {
  void foo() throws Exception {
    A.class.getConstructor(B[].class);
    A.class.getMethod("bar", int.class, B[].class);
    A.class.getMethod("bar", B.class);

    A.class.getConstructor<warning descr="Cannot resolve constructor with specified argument types">(B.class)</warning>;
    A.class.getMethod(<warning descr="Cannot resolve method 'bar' with specified argument types">"bar"</warning>, int.class, B.class);

    Class<?> a = Class.forName("A");
    Class<?> b = Class.forName("B");
    Object bb = Array.newInstance(b, 1);
    Class<?> bc = bb.getClass();
    Object o = b.newInstance();
    Class<?> oc = o.getClass();
    a.getConstructor(bc);
    a.getMethod("bar", String.class, bc);
    a.getMethod("bar", oc);

    a.getConstructor<warning descr="Cannot resolve constructor with specified argument types">(B.class)</warning>;
    a.getMethod(<warning descr="Cannot resolve method 'bar' with specified argument types">"bar"</warning>, String.class, B.class);
    a.getMethod(<warning descr="Cannot resolve method 'bar' with specified argument types">"bar"</warning>, B[].class);

    ourA.getConstructor(bc);
    ourA.getMethod("bar", String.class, Array.newInstance(myB, 0).getClass());
    ourA.getMethod("bar", myB.newInstance().getClass());
  }

  static final Class<?> ourA;
  static {
    try {
      ourA = Class.forName("A");
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  final Class<?> myB;
  {
    myB = Class.forName("B");
  }

  final Class<?> myB1;

  Vararg(int n) throws Exception {
    Object bb = Array.newInstance(myB, n);
    Class<?> bc = bb.getClass();
    ourA.getConstructor(bc);
    ourA.getMethod("bar", String.class, bc);
    myB1 = B.class;
  }

  void foo1() throws Exception {
    ourA.getMethod("bar", myB1);
    ourA.getMethod(<warning descr="Cannot resolve method 'bar' with specified argument types">"bar"</warning>, myB1, String.class);
  }
}

class A {
  public A(B... b) {}
  public void bar(int n, B... b) {}
  public void bar(String s, B... b) {}
  public void bar(B b) {}
}

class B {}
