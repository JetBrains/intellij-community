interface I {
    void m(int x);
}

class Test {
    void foo(Object x) {}

    void bar() {
        foo(!<error descr="Lambda expression not expected here">(int x)-> {}</error>);
        foo(<error descr="Lambda expression not expected here">(int x)-> { }</error> instanceof Object );
    }

    I bazz() {
        foo((I)(int x)-> { });
        I o = (I)(int x)-> { };
        return (int x) -> {};
    }
}

interface II {
  boolean m(String s);
}
class Test1 {
  void bar(boolean b){
    II ik = b ? (s)-> true : (s)->false;
    II ik1 = (II)((b ? <error descr="Lambda expression not expected here">(s)-> true</error> : <error descr="Lambda expression not expected here">(s)->false</error>));
    II ik2 = (II)(ik1 = (b ? (s)-> true : (s)->false));
    (b ? <error descr="Lambda expression not expected here">(s) -> true</error> : ik).m("");
  }
}

class Test2 {
  void f() {
    final Runnable one=true ? null : true ? () -> {} : () -> {};
  }
}