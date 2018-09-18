class App {
  void test(A a, A b) {
    if(a.field != b.field) {
      if(<warning descr="Condition 'a == b' is always 'false'">a == b</warning>) {
        System.out.println("Impossible");
      }
    }
  }

  public static void main(String[] args) {
    A a1 = new A();
    assert a1.field == 0;
    A a2 = a1;
    assert a2.field == 0;
    a1.field = 10;
    assert <warning descr="Condition 'a2.field == 10' is always 'true'">a2.field == 10</warning>;
  }
}

class A {
  public int field = 0;
}

class X {
  X foo;

  void test() {
    final X localFoo = foo.getX();

    if (localFoo == null) {
      boolean nonNull = foo != null;
    }
  }

  native X getX();
}


