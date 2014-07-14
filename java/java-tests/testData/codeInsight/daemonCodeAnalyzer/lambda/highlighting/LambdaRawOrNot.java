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
    return exerciseOps(data, (u, v) -> u.equals(v), terminal);
  }
}

class Test3 {
  interface I<Y> {
    void m(Y y);
  }

  static <T> void bar(I<T> i, List<T> l){
    bar(x -> {}, l);
    bar(x -> {}, null);
    bar((I<T>)x -> {}, null);
    bar((T x) -> {}, null);
    bar(x -> {}, new ArrayList<T>());
    bar(x -> {}, new ArrayList());
  }

  static {
    bar(x->{}, new ArrayList());
  }
}


class Test4 {
  protected <T, U> U exerciseOps(TestData<T> data, TerminalOp1<T, U> terminal, IntermediateOp1... ops) {
    return exerciseOps(data, (u, v) -> u.equals(v), terminal, ops);
  }

  protected static <T, U> U exerciseOps(TestData<T> data,
                                        BiPredicate1<U, U> equalator,
                                        TerminalOp1<T, U> terminalOp,
                                        IntermediateOp1[] ops) {
    return null;
  }

  public interface IntermediateOp1<T,U> {

  }
  public interface BiPredicate1<T, U> extends IntermediateOp1<T, U>{
    boolean _(T t, U u);
  }

  public interface TerminalOp1<T, U> extends IntermediateOp1<T, U> {}

}

class Test5 {
  {
    Block empty = x -> {};
    Block<?> empty1 = x -> {};
    System.out.println((Block) x -> {});
  }

  interface Block<T> {
    void apply(T t);
  }
}