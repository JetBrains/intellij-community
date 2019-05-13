import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

class Main {
  void fooInt() throws Throwable {
    MethodHandle handle = MethodHandles.lookup().findStaticSetter(Test.class, "n", int.class);
    Test instance = new Test();

    handle.invokeWithArguments(1);
    handle.invoke(2);
    handle.invokeExact(3);

    Object object = 123;
    handle.invokeWithArguments(object);
    handle.invoke(object);
    handle.invokeExact(<warning descr="Argument type should be exactly 'int'">object</warning>);

    Object objectResult = <warning descr="Returned value is always 'null'">handle.invokeWithArguments</warning>(1);
    objectResult = <warning descr="Returned value is always 'null'">handle.invoke</warning>(2);
    objectResult = <warning descr="Return type is 'void'">handle.invokeExact</warning>(3);
    Object objectResult1 = <warning descr="Return type is 'void'">handle.invokeExact</warning>(3);

    handle.invokeWithArguments(new Object[]{1});
    handle.invoke(<warning descr="Argument is not assignable to 'int'">new Object[]{2}</warning>);
    handle.invokeExact(<warning descr="Argument type should be exactly 'int'">new Object[]{3}</warning>);

    handle.invokeWithArguments(Integer.valueOf(1));
    handle.invoke(Integer.valueOf(2));
    handle.invokeExact(<warning descr="Argument type should be exactly 'int'">Integer.valueOf(3)</warning>);

    handle.invokeWithArguments( <warning descr="Argument is not assignable to 'int'">"a"</warning>);
    handle.invoke(<warning descr="Argument is not assignable to 'int'">"b"</warning>);
    handle.invokeExact(<warning descr="Argument type should be exactly 'int'">"c"</warning>);

    String incompatibleResult1 = (String) <warning descr="Returned value is always 'null'">handle.invokeWithArguments</warning>(1);
    String incompatibleResult2 = (String) <warning descr="Returned value is always 'null'">handle.invoke</warning>(2);
    String incompatibleResult3 = (String) <warning descr="Return type is 'void'">handle.invokeExact</warning>(3);

    Object nullArg = null;
    handle.invokeWithArguments(<warning descr="Argument of type 'int' cannot be 'null'">nullArg</warning>);
    handle.invoke(<warning descr="Argument is not assignable to 'int'">null</warning>);
    handle.invokeExact(<warning descr="Argument type should be exactly 'int'">null</warning>);

    handle.invokeWithArguments<warning descr="One argument is expected">()</warning>;
    handle.invoke<warning descr="One argument is expected">()</warning>;
    handle.invokeExact<warning descr="One argument is expected">()</warning>;

    handle.invokeWithArguments(<warning descr="Argument is not assignable to 'int'">instance</warning>);
    handle.invoke(<warning descr="Argument is not assignable to 'int'">instance</warning>);
    handle.invokeExact(<warning descr="Argument type should be exactly 'int'">instance</warning>);

    handle.invokeWithArguments<warning descr="One argument is expected">(1, "a")</warning>;
    handle.invoke<warning descr="One argument is expected">(2, "b")</warning>;
    handle.invokeExact<warning descr="One argument is expected">(3, "c")</warning>;
  }

  void fooStr() throws Throwable {
    final MethodHandles.Lookup lookup = MethodHandles.lookup();
    final MethodHandle handle = lookup.findStaticSetter(Test.class, "s", String.class);
    final Test instance = new Test();

    handle.invokeWithArguments("a");
    handle.invoke("b");
    handle.invokeExact("c");

    Object object = "abc";
    handle.invokeWithArguments(object);
    handle.invoke(object);
    handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">object</warning>);

    Object objectResult = <warning descr="Returned value is always 'null'">handle.invokeWithArguments</warning>("a");
    objectResult = <warning descr="Returned value is always 'null'">handle.invoke</warning>("b");
    objectResult = <warning descr="Return type is 'void'">handle.invokeExact</warning>("c");
    Object objectResult1 = <warning descr="Return type is 'void'">handle.invokeExact</warning>("c");

    handle.invokeWithArguments(new Object[]{"a"});
    handle.invoke(<warning descr="Argument is not assignable to 'java.lang.String'">new Object[]{"b"}</warning>);
    handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">new Object[]{"c"}</warning>);

    handle.invokeWithArguments(<warning descr="Argument is not assignable to 'java.lang.String'">1</warning>);
    handle.invoke(<warning descr="Argument is not assignable to 'java.lang.String'">2</warning>);
    handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">3</warning>);

    int incompatibleResult1 = (int) <warning descr="Returned value is always 'null'">handle.invokeWithArguments</warning>("a");
    int incompatibleResult2 = (int) <warning descr="Returned value is always 'null'">handle.invoke</warning>("b");
    int incompatibleResult3 = (int) <warning descr="Return type is 'void'">handle.invokeExact</warning>("c");

    Object nullArg = null;
    handle.invokeWithArguments(nullArg);
    handle.invoke(null);
    handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">null</warning>);
    handle.invokeExact((String)null);

    handle.invokeWithArguments<warning descr="One argument is expected">()</warning>;
    handle.invoke<warning descr="One argument is expected">()</warning>;
    handle.invokeExact<warning descr="One argument is expected">()</warning>;

    handle.invokeWithArguments(<warning descr="Argument is not assignable to 'java.lang.String'">instance</warning>);
    handle.invoke(<warning descr="Argument is not assignable to 'java.lang.String'">instance</warning>);
    handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">instance</warning>);

    handle.invokeWithArguments<warning descr="One argument is expected">("a", 1)</warning>;
    handle.invoke<warning descr="One argument is expected">("b", 2)</warning>;
    handle.invokeExact<warning descr="One argument is expected">("c", 3)</warning>;

    handle.invokeWithArguments(charSequence());
    handle.invoke(charSequence());
    handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">charSequence()</warning>);
  }

  private static CharSequence charSequence() { return "abc"; }
}

class Super {}

class Test extends Super {
  public static int n;
  public static String s;
}