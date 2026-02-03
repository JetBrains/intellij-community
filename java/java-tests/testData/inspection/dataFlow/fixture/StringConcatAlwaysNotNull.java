import org.jetbrains.annotations.NotNull;

class X {
  @NotNull
  String foo() {return "";}

  void bar() {
    String o = foo();
    o += "";
    if (<warning descr="Condition 'o != null' is always 'true'">o != null</warning>) {

    }
  }

}