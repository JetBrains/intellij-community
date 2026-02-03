package com.siyeh.igtest.bugs;

import java.util.*;
import java.util.function.*;

public class ReturnNull
{

    public Object bar()
    {
        return <warning descr="Return of 'null'">null</warning>;
    }

    public int[] bar2()
    {
        return <warning descr="Return of 'null'">null</warning>;
    }

    private String get() {
        return <warning descr="Return of 'null'">null</warning>;
    }
}
interface A<T> {
    T m();
}
class B implements A<Void> {
    public Void m() {
        return  null;
    }

    void bar() {
        Map<String, String> map = new HashMap<>();
        map.compute("foo", (k, v) -> {
            return Math.random() < 0.5 ? v : null; // <- false-positive warning 'return of null'
        });
        map.compute("foo", (k, v) -> Math.random() < 0.5 ? v : null);
        final BiFunction<String, String, String> x = (k, v) -> Math.random() < 0.5 ? k : <warning descr="Return of 'null'">null</warning>;
        final BiFunction<String, String, String> y = (k, v) -> {
            return Math.random() < 0.5 ? k : <warning descr="Return of 'null'">null</warning>;
        };
        final BiFunction<String, String, String> z = new BiFunction<String, String, String>() {
            @Override
            public String apply(String k, String v) {
                return Math.random() < 0.5 ? k : <warning descr="Return of 'null'">null</warning>;
            }
        };
    }

    static <T extends @Nullable CharSequence> void nnn(T t) {
        System.out.println(t.length()); // correctly understands that t is nullable and warns
        java.util.function.Function<String, T> f = s  -> null; // should not warn here
    }
}
class Test {
    void foo() {
        Function<String, @Nullable String> f = s  -> null;
    }
}
class WithContract {
  @org.jetbrains.annotations.Contract("!null -> !null; null -> null")
  public static String maybeTrim(String s) {
    if (s == null) return null;
    return s.trim();
  }

  public static String maybeTrimNoContract(String s) {
    if (s == null) return <warning descr="Return of 'null'">null</warning>;
    return s.trim();
  }
}