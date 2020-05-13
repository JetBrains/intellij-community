import org.jetbrains.annotations.NotNull;

class ImpossibleType {
  interface Foo {}

  <T extends String & Foo> void foo(T t, @NotNull T t1) {
    if (<warning descr="Condition 't == null' is always 'true'">t == null</warning>) { }
    // It's unclear what we should do in this case; formally this method cannot be executed at all because there's no
    // possible value for t1.
    if (<warning descr="Condition 't1 != null' is always 'false'">t1 != null</warning>) {}
    if (<warning descr="Condition 't1 == null' is always 'true'">t1 == null</warning>) {}
  }
}