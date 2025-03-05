interface I {
    void m(int x);
}

class Test {
    void foo(Object x) {}

    void bar() {
        foo(!<error descr="Unexpected lambda expression">(int x)-> {}</error>);
        foo(<error descr="Unexpected lambda expression">(int x)-> { }</error> instanceof Object );
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
    II ik1 = (II)((b ? <error descr="Unexpected lambda expression">(s)-> true</error> : <error descr="Unexpected lambda expression">(s)->false</error>));
    II ik2 = (II)(ik1 = (b ? (s)-> true : (s)->false));
    (b ? <error descr="Unexpected lambda expression">(s) -> true</error> : ik).m("");
  }
}

class Test2 {
  void f() {
    final Runnable one=true ? null : true ? () -> {} : () -> {};
  }
}