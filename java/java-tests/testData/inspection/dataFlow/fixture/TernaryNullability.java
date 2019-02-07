import org.jetbrains.annotations.*;

final class MyClass {
  void cons(Object x) {}
  
  void test(@Nullable Object a, @Nullable Object b, @Nullable Object c, boolean f1, boolean f2) {
    cons(<warning descr="Argument 'f1 ? a : b' might be null but passed to non-annotated parameter">f1 ? a : b</warning>);
    cons(f1 ? <warning descr="Argument 'a' might be null but passed to non-annotated parameter">a</warning> : new Object());
    cons(f1 ? new Object() : <warning descr="Argument 'a' might be null but passed to non-annotated parameter">a</warning>);
    cons(f1 ? <warning descr="Argument 'f2 ? a : (Object)b' might be null but passed to non-annotated parameter">f2 ? a : (Object)b</warning> : new Object());
    cons(f1 ? f2 ? <warning descr="Argument 'a' might be null but passed to non-annotated parameter">a</warning> : new Object() : <warning descr="Argument 'b' might be null but passed to non-annotated parameter">b</warning>);
    cons(f1 ? f2 ? new Object() : <warning descr="Argument 'a' might be null but passed to non-annotated parameter">a</warning> : <warning descr="Argument 'b' might be null but passed to non-annotated parameter">b</warning>);
    cons(<warning descr="Argument 'f1 ? f2 ? a : b : ((Object)c)' might be null but passed to non-annotated parameter">f1 ? f2 ? a : b : ((Object)c)</warning>);
    cons(f1 ? <warning descr="Argument 'f2 ? a : b' might be null but passed to non-annotated parameter">f2 ? a : b</warning> : f2 ? <warning descr="Argument 'c' might be null but passed to non-annotated parameter">c</warning> : new Object());
  }
  
  @NotNull String testReturn(boolean b) {
    return b ? "hello" : <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
  }
  
  @NotNull Object testReturn2(boolean b, @Nullable Object o1, @Nullable Object o2) {
    return <warning descr="Expression 'b ? o1 : o2' might evaluate to null but is returned by the method declared as @NotNull">b ? o1 : o2</warning>;
  }
}