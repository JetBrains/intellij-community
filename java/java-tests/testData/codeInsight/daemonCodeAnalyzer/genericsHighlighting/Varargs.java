class clazz1 {
    clazz1(int... args) { args = null; }
    public static class Myclazz1 extends clazz1 {}
}

class AmbiguousReference {
  void test() {
    doSomething<error descr="Ambiguous method call: both 'AmbiguousReference.doSomething(String, Number...)' and 'AmbiguousReference.doSomething(Number...)' match">(null, 1)</error>;
  }

  void doSomething(String s, Number... n) {
    s+=n;
  }

  void doSomething(Number... n) {
    n.hashCode();
  }
}

class OK {
    protected void fff() {
        find("");
    }
    public void find(String queryString)  {
       queryString.hashCode();
    }

    public void find(final String queryString, final Object... values)  {
       queryString.hashCode();
       values.hashCode();
    }

}