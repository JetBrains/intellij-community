import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract(<warning descr="A contract clause must be in form arg1, ..., argN -> return-value">"a"</warning>)
  void malformedContract() {}

  @Contract(<warning descr="Method takes 2 parameters, while contract clause number 1 expects 1">"null -> _"</warning>)
  void wrongParameterCount(Object a, boolean b) {}

  @Contract(pure=true)
  void voidPureMethod() {}

  @Contract(<warning descr="Method returns void but the contract specifies null">"->null"</warning>)
  public native void throwMe();

  @Contract(<warning descr="Method returns boolean but the contract specifies null">"->null"</warning>)
  public native boolean wrongReturnType();

  @Contract(<warning descr="Method returns String but the contract specifies true">"->true"</warning>)
  public native String wrongReturnType2();

}
