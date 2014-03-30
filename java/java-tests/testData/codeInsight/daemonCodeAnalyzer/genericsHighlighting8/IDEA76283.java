import java.util.*;
class IDEA76283 {
}

interface Parametrized<T extends Number> {
}

class Bug1<T extends Number> {
    <I extends Number> Parametrized<I> foo(Parametrized<I> param) {
        return null;
    }

    void bug1(Parametrized<? super T> param) {
        foo(param);
    }

    void bug2(Set<Parametrized<? extends Number>> parametrizeds) {
        Set<Parametrized<?>> items = parametrizeds;
    }

    void bug3(Set<Parametrized<?>> parametrizeds) {
        Set<Parametrized<?>> items = parametrizeds;
    }

    void bug4(Set<Parametrized<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>>> parametrizeds) {
        <error descr="Incompatible types. Found: 'java.util.Set<Parametrized<? extends java.lang.String>>', required: 'java.util.Set<Parametrized<?>>'">Set<Parametrized<?>> items = parametrizeds;</error>
    }

    void bug5(Set<Parametrized<? extends Integer>> parametrizeds) {
        <error descr="Incompatible types. Found: 'java.util.Set<Parametrized<? extends java.lang.Integer>>', required: 'java.util.Set<Parametrized<?>>'">Set<Parametrized<?>> items = parametrizeds;</error>
    }

    void bug6(Set<Parametrized<? super Number>> parametrizeds) {
        <error descr="Incompatible types. Found: 'java.util.Set<Parametrized<? super java.lang.Number>>', required: 'java.util.Set<Parametrized<?>>'">Set<Parametrized<?>> items = parametrizeds;</error>
    }

    void bug7(Set<Parametrized<? super Integer>> parametrizeds) {
        <error descr="Incompatible types. Found: 'java.util.Set<Parametrized<? super java.lang.Integer>>', required: 'java.util.Set<Parametrized<?>>'">Set<Parametrized<?>> items = parametrizeds;</error>
    }

    void bug8(Set<Parametrized<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>>> parametrizeds) {
        <error descr="Incompatible types. Found: 'java.util.Set<Parametrized<? super java.lang.String>>', required: 'java.util.Set<Parametrized<?>>'">Set<Parametrized<?>> items = parametrizeds;</error>
    }

    void bug9(Set<Parametrized<<error descr="Type parameter '? super Object' is not within its bound; should extend 'java.lang.Number'">? super Object</error>>> parametrizeds) {
        <error descr="Incompatible types. Found: 'java.util.Set<Parametrized<? super java.lang.Object>>', required: 'java.util.Set<Parametrized<?>>'">Set<Parametrized<?>> items = parametrizeds;</error>
    }
}

