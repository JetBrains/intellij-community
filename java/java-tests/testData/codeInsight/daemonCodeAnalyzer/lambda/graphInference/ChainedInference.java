import java.util.*;

class Main {

    void foo(List<Integer> list) {
        bar(list, i -> i.intValue(), i -> i.<error descr="Cannot resolve method 'unknown' in 'Integer'">unknown</error>());
        bar1(list, i -> i.intValue(), i -> i.<error descr="Cannot resolve method 'unknown' in 'Integer'">unknown</error>());
    }

    <U, S_IN, S_OUT, R> R bar(List<S_IN> list,
                              Fun<S_IN, S_OUT> f1,
                              Fun<S_OUT, R> f2) {
        return null;
    }

    <R, S_IN, S_OUT> R bar1(List<S_IN> list,
                            Fun<S_IN, S_OUT> f1,
                            Fun<S_OUT, R> f2) {
        return null;
    }


    public interface Fun<T, R> {
        public R f(T t);
    }
}
