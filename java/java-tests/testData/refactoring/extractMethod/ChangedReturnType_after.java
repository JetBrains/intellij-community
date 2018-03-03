import org.jetbrains.annotations.Nullable;

class X {
  void foo(java.util.List l) {
    for (Object o : l) {
        Object x = newMethod(o);
        if (x == null) continue;
        System.out.println(x);
    }
  }

    @Nullable
    private Object newMethod(Object o) {
        if (o == null) return null;
        String x = bar(o);
        return x;
    }

    private String bar(Object o) {
    return "";
  }
}
