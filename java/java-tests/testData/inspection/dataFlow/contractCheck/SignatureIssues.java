import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract("<warning descr="A contract clause must be in form arg1, …, argN -> return-value">a</warning>")
  void malformedContract() {}

  @Contract("<warning descr="Method takes 2 parameters, while contract clause 'null -> _' expects 1">null -> _</warning>")
  void wrongParameterCount(Object a, boolean b) {}

  @Contract(pure=true)
  void voidPureMethod() {}

  @Contract("-><warning descr="Contract return value 'null': not applicable to primitive return type 'void'">null</warning>")
  public native void throwMe();

  @Contract("-><warning descr="Contract return value 'null': not applicable to primitive return type 'boolean'">null</warning>")
  public native boolean wrongReturnType();

  @Contract("-><warning descr="Contract return value 'true': method return type must be 'boolean'">true</warning>")
  public native String wrongReturnType2();

  @Contract("-><warning descr="Contract return value 'param1': not applicable to method that has no parameters">param1</warning>")
  public native String absentParameter();

  @Contract("_-><warning descr="Contract return value 'param2': not applicable to method that has one parameter">param2</warning>")
  public native String absentParameter2(String x);

  @Contract("_,_-><warning descr="Contract return value 'param3': not applicable to method that has 2 parameters">param3</warning>")
  public native String absentParameter3(String x, int y);

  @Contract("_-><warning descr="Contract return value 'param1': return type 'String' must be convertible from parameter type 'Integer'">param1</warning>")
  public native String wrongParameterType(Integer x);

  @Contract("_->param1")
  public native Object okParameterType(Integer x);

  @Contract("-><warning descr="Contract return value 'new': not applicable to primitive return type 'boolean'">new</warning>")
  public native boolean wrongReturnTypeNew();

  @Contract("-><warning descr="Contract return value 'this': not applicable to primitive return type 'boolean'">this</warning>")
  public native boolean wrongReturnTypeThis();

  @Contract("-><warning descr="Contract return value 'this': method return type should be compatible with method containing class">this</warning>")
  public native String wrongReturnTypeThis2();

  public native Foo okReturnTypeThis();

  @Contract("-><warning descr="Contract return value 'this': not applicable to static method">this</warning>")
  public native static Foo staticThis();

  @Contract("-><warning descr="Return value should be one of: null, !null, true, false, this, new, paramN, fail, _. Found: foo">foo</warning>")
  public native void invalidReturn();

  @Contract("<warning descr="Parameter 's' has 'String' type (expected boolean)">true</warning> -> fail")
  public native void invalidType(String s);

  @Contract("<warning descr="Parameter 's' has primitive type 'int', so 'null' is not applicable">null</warning> -> fail")
  public native void invalidType(int s);
}
