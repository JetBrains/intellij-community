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

    try (MyResource r2 = <warning descr="Variable 'r2' initializer 'new MyResource()'  is redundant">new MyResource()</warning>) {
      r2 = null;  // todo: check for NPE
      System.out.println(r2);
    }

    MyResource r3 = null;
    System.out.println(r3);
    try (r3 = <warning descr="The value 'new MyResource()' assigned to r3  is never used">new MyResource()</warning>) { }

    try (MyResource <warning descr="Variable 'r4' is never used">r4</warning> = new MyResource()) { }

    try (MyResource r5 = new MyResource()) {
      System.out.println(r5);
      r5 = <warning descr="The value 'new MyResource()' assigned to r5  is never used">new MyResource()</warning>;
    }

    MyResource <warning descr="Variable 'r6' is never assigned">r6</warning>;
    try (MyResource r = <error descr="Variable 'r6' might not have been initialized">r6</error>) {
      System.out.println(r);
    }
  }
}