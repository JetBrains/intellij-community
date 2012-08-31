import java.util.*;
class TestData<K> {}
interface TerminalOp<L, M> extends IntermediateOp<L, M> {}
interface IntermediateOp<L1, M1> { boolean _(L1 l, M1 m);}


class Test1 {
  protected <T, U> U exerciseOps(TestData<T> data, TerminalOp<T, U> terminal, IntermediateOp<T, U>... ops) {
    return exerciseOps(data, (u, v) -> u.equals(v), terminal);
  }
}

class Test2 {
  protected <T, U> U exerciseOps(TestData<T> data, TerminalOp<T, U> terminal, IntermediateOp... ops) {
    return exerciseOps(data, <error descr="Cyclic inference">(u, v) -> u.equals(v)</error>, terminal);
  }
}

class Test3 {
  interface I<Y> {
    void m(Y y);
  }

  static <T> void bar(I<T> i, List<T> l){
    bar(x -> {}, l);
    bar(<error descr="Cyclic inference">x -> {}</error>, null);
    bar((I<T>)x -> {}, null);
    bar((T x) -> {}, null);
    bar(x -> {}, new ArrayList<T>());
    bar(<error descr="Cyclic inference">x -> {}</error>, new ArrayList());
  }

  static {
    bar(<error descr="Cyclic inference">x->{}</error>, new ArrayList());
  }
}
