import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

class Main {
  void fooInt() throws Throwable {
    MethodHandle handle = MethodHandles.lookup().findSetter(Test.class, "n", int.class);
    Test instance = new Test();

    handle.invokeWithArguments(instance, 1);
    handle.invoke(instance, 2);
    handle.invokeExact(instance, 3);

    Object object = 123;
    handle.invokeWithArguments(instance, object);
    handle.invoke(instance, object);
    handle.invokeExact(instance, <warning descr="Argument type should be exactly 'int'">object</warning>);

    Object objectResult = <warning descr="Returned value is always 'null'">handle.invokeWithArguments</warning>(instance, 1);
    objectResult = <warning descr="Returned value is always 'null'">handle.invoke</warning>(instance, 2);
    objectResult = <warning descr="Return type is 'void'">handle.invokeExact</warning>(instance, 3);
    Object objectResult1 = <warning descr="Return type is 'void'">handle.invokeExact</warning>(instance, 3);

    handle.invokeWithArguments(new Object[]{instance, 1});
    handle.invoke<warning descr="2 arguments are expected">(new Object[]{instance, 2})</warning>;
    handle.invokeExact<warning descr="2 arguments are expected">(new Object[]{instance, 3})</warning>;

    handle.invokeWithArguments(instance, Integer.valueOf(1));
    handle.invoke(instance, Integer.valueOf(2));
    handle.invokeExact(instance, <warning descr="Argument type should be exactly 'int'">Integer.valueOf(3)</warning>);

    handle.invokeWithArguments(instance, <warning descr="Argument is not assignable to 'int'">"a"</warning>);
    handle.invoke(instance, <warning descr="Argument is not assignable to 'int'">"b"</warning>);
    handle.invokeExact(instance, <warning descr="Argument type should be exactly 'int'">"c"</warning>);

    String incompatibleResult1 = (String) <warning descr="Returned value is always 'null'">handle.invokeWithArguments</warning>(instance, 1);
    String incompatibleResult2 = (String) <warning descr="Returned value is always 'null'">handle.invoke</warning>(instance, 2);
    String incompatibleResult3 = (String) <warning descr="Return type is 'void'">handle.invokeExact</warning>(instance, 3);

    handle.invokeWithArguments( <warning descr="Call receiver is 'null'">null</warning>, 1);
    handle.invoke(<warning descr="Call receiver is 'null'">null</warning>, 2);
    handle.invokeExact(<warning descr="Call receiver is 'null'">null</warning>, 3);

    handle.invokeWithArguments(instance, <warning descr="Argument is not assignable to 'int'">null</warning>);
    handle.invoke(instance, <warning descr="Argument is not assignable to 'int'">null</warning>);
    handle.invokeExact(instance, <warning descr="Argument type should be exactly 'int'">null</warning>);

    handle.invokeWithArguments(<warning descr="Call receiver type is incompatible: 'Test' is expected">42</warning>, 1);
    handle.invoke(<warning descr="Call receiver type is incompatible: 'Test' is expected">42</warning>, 2);
    handle.invokeExact(<warning descr="Call receiver type is incompatible: 'Test' is expected">42</warning>, 3);

    handle.invokeWithArguments<warning descr="2 arguments are expected">(1)</warning>;
    handle.invoke<warning descr="2 arguments are expected">(2)</warning>;
    handle.invokeExact<warning descr="2 arguments are expected">(3)</warning>;

    int tooManyArguments1 = (int) handle.invokeWithArguments<warning descr="2 arguments are expected">(instance, 1, "a")</warning>;
    int tooManyArguments2 = (int) handle.invoke<warning descr="2 arguments are expected">(instance, 2, "b")</warning>;
    int tooManyArguments3 = (int) handle.invokeExact<warning descr="2 arguments are expected">(instance, 3, "c")</warning>;
  }

  void fooStr() throws Throwable {
    final MethodHandles.Lookup lookup = MethodHandles.lookup();
    final MethodHandle handle = lookup.findSetter(Test.class, "s", String.class);
    final Test instance = new Test();

    handle.invokeWithArguments(instance, "a");
    handle.invoke(instance, "b");
    handle.invokeExact(instance, "c");

    Object object = "abc";
    handle.invokeWithArguments(instance, object);
    handle.invoke(instance, object);
    handle.invokeExact(instance, <warning descr="Argument type should be exactly 'java.lang.String'">object</warning>);

    Object objectResult = <warning descr="Returned value is always 'null'">handle.invokeWithArguments</warning>(instance, "a");
    objectResult = <warning descr="Returned value is always 'null'">handle.invoke</warning>(instance, "b");
    objectResult = <warning descr="Return type is 'void'">handle.invokeExact</warning>(instance, "c");
    Object objectResult1 = <warning descr="Return type is 'void'">handle.invokeExact</warning>(instance, "c");

    handle.invokeWithArguments(new Object[]{instance, "a"});
    handle.invoke<warning descr="2 arguments are expected">(new Object[]{instance, "b"})</warning>;
    handle.invokeExact<warning descr="2 arguments are expected">(new Object[]{instance, "c"})</warning>;

    handle.invokeWithArguments(instance, <warning descr="Argument is not assignable to 'java.lang.String'">1</warning>);
    handle.invoke(instance, <warning descr="Argument is not assignable to 'java.lang.String'">2</warning>);
    handle.invokeExact(instance, <warning descr="Argument type should be exactly 'java.lang.String'">3</warning>);

    int incompatibleResult1 = (int) <warning descr="Returned value is always 'null'">handle.invokeWithArguments</warning>(instance, "a");
    int incompatibleResult2 = (int) <warning descr="Returned value is always 'null'">handle.invoke</warning>(instance, "b");
    int incompatibleResult3 = (int) <warning descr="Return type is 'void'">handle.invokeExact</warning>(instance, "c");

    handle.invokeWithArguments(<warning descr="Call receiver is 'null'">null</warning>, "a");
    handle.invoke(<warning descr="Call receiver is 'null'">null</warning>, "b");
    handle.invokeExact(<warning descr="Call receiver is 'null'">null</warning>, "c");

    handle.invokeWithArguments(instance, null);
    handle.invoke(instance, null);
    handle.invokeExact(instance, <warning descr="Argument type should be exactly 'java.lang.String'">null</warning>);
    handle.invokeExact(instance, (String)null);

    handle.invokeWithArguments(<warning descr="Call receiver type is incompatible: 'Test' is expected">"x"</warning>, "a");
    handle.invoke(<warning descr="Call receiver type is incompatible: 'Test' is expected">"x"</warning>, "b");
    handle.invokeExact(<warning descr="Call receiver type is incompatible: 'Test' is expected">"x"</warning>, "c");

    handle.invokeWithArguments<warning descr="2 arguments are expected">(instance)</warning>;
    handle.invoke<warning descr="2 arguments are expected">(instance)</warning>;
    handle.invokeExact<warning descr="2 arguments are expected">(instance)</warning>;

    handle.invokeWithArguments<warning descr="2 arguments are expected">(instance, "a", 1)</warning>;
    handle.invoke<warning descr="2 arguments are expected">(instance, "b", 2)</warning>;
    handle.invokeExact<warning descr="2 arguments are expected">(instance, "c", 3)</warning>;

    handle.invokeWithArguments(instance, charSequence());
    handle.invoke(instance, charSequence());
    handle.invokeExact(instance, <warning descr="Argument type should be exactly 'java.lang.String'">charSequence()</warning>);
  }

  private static CharSequence charSequence() { return "abc"; }
}

class Super {}

class Test extends Super {
  public int n;
  public String s;
}