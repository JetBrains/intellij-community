import java.lang.Exception;

class C {
  static class MyResource implements AutoCloseable {
    @Override public void close() { }
  }

  void m1() throws Exception {
    MyResource r1;
    try (r1 = new MyResource()) {
      System.out.println(r1);
    }

    try (MyResource r2 = new MyResource()) {
      System.out.println(r2);
    }

    MyResource r3 = new MyResource();
    try (MyResource r = r3) {
      System.out.println(r);
    }
  }

  void m2() throws Exception {
    MyResource r1 = <warning descr="Variable 'r1' initializer 'null'  is redundant">null</warning>;
    try (r1 = new MyResource()) {
      System.out.println(r1);
    }

    MyResource r2 = null;
    System.out.println(r2);
    try (r2 = <warning descr="The value 'new MyResource()' assigned to r2  is never used">new MyResource()</warning>) { }

    try (MyResource <warning descr="Variable 'r3' is never used">r3</warning> = new MyResource()) { }

    MyResource <warning descr="Variable 'r4' is never assigned">r4</warning>;
    try (MyResource r = <error descr="Variable 'r4' might not have been initialized">r4</error>) {
      System.out.println(r);
    }
  }
}