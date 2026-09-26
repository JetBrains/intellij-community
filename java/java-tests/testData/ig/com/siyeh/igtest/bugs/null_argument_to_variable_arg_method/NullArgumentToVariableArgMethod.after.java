package com.siyeh.igtest.bugs.null_argument_to_variable_arg_method;

public class NullArgumentToVariableArgMethod {
    public void foo(String[] ss)
    {
        String.format("%s", (Object) null);
        String.format("%d", 1);
        String.format("%s", (Object) ss);

        CompletableFuture[] futures = {};
        CompletableFuture.allOf(futures);
    }
}
class CompletableFuture<T> {
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs) {
        return null;
    }
}
class X {
    void a() {
        String[] array = {"one", "two"};
        final String join = join(" ", array);
    }

    public static String join(CharSequence delimiter, CharSequence... elements) {
        return "";
    }
}
class AB {
    AB(String msg, Object... args) {}

    void m(String e) {
        new AB("reactor", (Object) null);
    }
}
enum Inequitity {
    A((String) null), B;

    Inequitity(String... ss) {}
}
class Demo<E> {
  public void call(E... e) {}

  public static void main(String[] args) {
    new Demo<String>().call((String) null);
  }
}