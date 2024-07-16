interface I {
	void f();
}
interface II extends I {
    @Override
    void f();
}
class C implements I {
    @Override
    public void f() {

    }
}
class Test extends <error descr="Cannot resolve symbol 'NonExisting'">NonExisting</error> {
  @Override
  void foo() {}
}
class Test1 implements <error descr="Cannot resolve symbol 'NonExistingIface'">NonExistingIface</error> {
  @Override
  void foo() {}
}
class Test2 extends Test {
  @Override
  void foo() {}

  <error descr="Method does not override method from its superclass">@Override</error>
  void foo2() {}
}