import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract(<warning descr="A contract clause must be in form arg1, ..., argN -> return-value">"a"</warning>)
  void malformedContract() {}

  @Contract(<warning descr="Method takes 2 parameters, while contract clause number 1 expects 1">"null -> _"</warning>)
  void wrongParameterCount(Object a, boolean b) {}

  @Contract(pure=true)
  void voidPureMethod() {}

  @Contract(<warning descr="Contract return value 'null': not applicable for primitive return type 'void'">"->null"</warning>)
  public native void throwMe();

  @Contract(<warning descr="Contract return value 'null': not applicable for primitive return type 'boolean'">"->null"</warning>)
  public native boolean wrongReturnType();

  @Contract(<warning descr="Contract return value 'true': method return type must be 'boolean'">"->true"</warning>)
  public native String wrongReturnType2();

  @Contract(<warning descr="Contract return value 'param1': not applicable for method which has 0 parameters">"->param1"</warning>)
  public native String absentParameter();

  @Contract(<warning descr="Contract return value 'param2': not applicable for method which has 1 parameter">"_->param2"</warning>)
  public native String absentParameter2(String x);

  @Contract(<warning descr="Contract return value 'param1': return type 'String' must be assignable from parameter type 'CharSequence'">"_->param1"</warning>)
  public native String wrongParameterType(CharSequence x);

  @Contract("_->param1")
  public native Object okParameterType(Integer x);

  @Contract(<warning descr="Contract return value 'new': not applicable for primitive return type 'boolean'">"->new"</warning>)
  public native boolean wrongReturnTypeNew();

  @Contract(<warning descr="Contract return value 'this': not applicable for primitive return type 'boolean'">"->this"</warning>)
  public native boolean wrongReturnTypeThis();

  @Contract(<warning descr="Contract return value 'this': method return type should be compatible with method containing class">"->this"</warning>)
  public native String wrongReturnTypeThis2();

  public native Foo okReturnTypeThis();

  @Contract(<warning descr="Contract return value 'this': not applicable for static method">"->this"</warning>)
  public native static Foo staticThis();

  @Contract(<warning descr="Return value should be one of: null, !null, true, false, this, new, paramN, fail, _. Found: foo">"->foo"</warning>)
  public native void invalidReturn();
}
