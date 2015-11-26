interface I {
  void m(int i);
}

class Foo {
  I ii = (<error descr="'@Override' not applicable to parameter">@Override</error> final int k) -> {
    int j = k;
  };
  I ii1 = (final int k) -> {
    <error descr="Incompatible types. Found: 'int', required: 'java.lang.String'">String s = k;</error>
  };

  void bazz() {
    bar(<error descr="Incompatible parameter types in lambda expression: expected int but found String">(String s)</error> -> {
      System.out.println(s);});
    bar((int i) -> {System.out.println(i);});
  }

  void bar(I i) { }
}

class ReturnTypeCompatibility {
  interface I1<L> {
    L m(L x);
  }

  static <P> void call(I1<P> i2) {
    i2.m(null);
  }

  public static void main(String[] args) {
    call((String i)->{ return i;});
    call(i->{ return i;});
    call(i->"");
    call(<error descr="no instance(s) of type variable(s) P exist so that P conforms to int">(int i)->{ return i;}</error>);
  }
}