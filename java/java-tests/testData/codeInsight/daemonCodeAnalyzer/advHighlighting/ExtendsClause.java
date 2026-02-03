class s {
  int i;
  class inn extends <error descr="Class name expected">i</error> {}
}

interface IFoo {
    public static final double UNKNOWN = -1;
}
class Foo implements <error descr="Class name expected">IFoo.UNKNOWN</error> {
  // error should be shown here
}
class Foo2 extends <error descr="Class name expected">IFoo.UNKNOWN</error> {
  // error should be shown here
}
class Xss implements <error descr="Class name expected">Integer.MAX_VALUE</error> {}
class Xss2 extends <error descr="Class name expected">Integer.MAX_VALUE</error> {}

