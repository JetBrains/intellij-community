import org.jetbrains.annotations.*;
class Class1 {
  public void f(@Nullable Object o) {
    o = o != null ? o : Class2.O;
    o.toString();
  }
}
class Class2 {
  static final Object O = new Object();
}