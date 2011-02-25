import java.lang.Exception;

class C {
  static class MyResource implements AutoCloseable {
    @Override public void close() { }
  }

  void m1() throws Exception {
    try (MyResource r1 = new MyResource()) {
      System.out.println(r1);
    }

    MyResource r2 = new MyResource();
    try (MyResource r = r2) {
      System.out.println(r);
      System.out.println(r2);
    }
  }

  void m2() throws Exception {
    // todo: test in IG
    //MyResource < warning descr="Local variable 'r1' is redundant">r1</warning> = null;
    //try (MyResource r = r1) {
    //  System.out.println(r);
    //}

    try (MyResource <warning descr="Variable 'r2' is never used">r2</warning> = new MyResource()) { }

    MyResource <warning descr="Variable 'r3' is never assigned">r3</warning>;
    try (MyResource r = <error descr="Variable 'r3' might not have been initialized">r3</error>) {
      System.out.println(r);
    }
  }
}