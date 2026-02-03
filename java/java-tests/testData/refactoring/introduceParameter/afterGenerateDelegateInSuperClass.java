abstract class A {
    void xxx() {
        xxx(239);
    }

    abstract void xxx(final int anObject);
}

class B implements A {
    public void xxx() {
        xxx(239);
    }

    public void xxx(final int anObject) {
    System.out.println(anObject);
  }

  static {
    A a = new B();
    a.xxx();
  }
}