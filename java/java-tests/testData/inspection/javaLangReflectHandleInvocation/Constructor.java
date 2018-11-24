import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

class Main {
  void fooInt() throws Throwable {
    MethodHandle handle = MethodHandles.lookup().findConstructor(Test.class, MethodType.methodType(void.class, int.class));

    Test exactSignature1 = (Test) handle.invokeWithArguments(1);
    Test exactSignature2 = (Test) handle.invoke(2);
    Test exactSignature3 = (Test) handle.invokeExact(3);

    Object object = 123;
    Test objectArgument1 = (Test) handle.invokeWithArguments(object);
    Test objectArgument2 = (Test) handle.invoke(object);
    Test objectArgument3 = (Test) handle.invokeExact(<warning descr="Argument type should be exactly 'int'">object</warning>);

    Object objectResult = handle.invokeWithArguments(1);
    objectResult = handle.invoke(2);
    objectResult = <warning descr="Should be cast to 'Test'">handle.invokeExact</warning>(3);
    Object objectResult1 = <warning descr="Should be cast to 'Test'">handle.invokeExact</warning>(3);

    Test argumentsArray1 = (Test) handle.invokeWithArguments(new Object[]{1});
    Test argumentsArray2 = (Test) handle.invoke(<warning descr="Argument is not assignable to 'int'">new Object[]{2}</warning>);
    Test argumentsArray3 = (Test) handle.invokeExact(<warning descr="Argument type should be exactly 'int'">new Object[]{3}</warning>);

    Test boxedArgument1 = (Test) handle.invokeWithArguments(Integer.valueOf(1));
    Test boxedArgument2 = (Test) handle.invoke(Integer.valueOf(2));
    Test boxedArgument3 = (Test) handle.invokeExact(<warning descr="Argument type should be exactly 'int'">Integer.valueOf(3)</warning>);


    Test incompatibleArgument1 = (Test) handle.invokeWithArguments(<warning descr="Argument is not assignable to 'int'">"a"</warning>);
    Test incompatibleArgument2 = (Test) handle.invoke(<warning descr="Argument is not assignable to 'int'">"b"</warning>);
    Test incompatibleArgument3 = (Test) handle.invokeExact(<warning descr="Argument type should be exactly 'int'">"c"</warning>);

    String incompatibleResult1 = (<warning descr="Should be cast to 'Test' or its superclass">String</warning>) handle.invokeWithArguments(1);
    String incompatibleResult2 = (<warning descr="Should be cast to 'Test' or its superclass">String</warning>) handle.invoke(2);
    String incompatibleResult3 = (<warning descr="Should be cast to 'Test'">String</warning>) handle.invokeExact(3);

    Object nullArg = null;
    Test nullArgument1 = (Test) handle.invokeWithArguments(<warning descr="Argument of type 'int' cannot be 'null'">nullArg</warning>);
    Test nullArgument2 = (Test) handle.invoke(<warning descr="Argument is not assignable to 'int'">null</warning>);
    Test nullArgument3 = (Test) handle.invokeExact(<warning descr="Argument type should be exactly 'int'">null</warning>);

    Test tooFewArguments1 = (Test) handle.invokeWithArguments<warning descr="One argument is expected">()</warning>;
    Test tooFewArguments2 = (Test) handle.invoke<warning descr="One argument is expected">()</warning>;
    Test tooFewArguments3 = (Test) handle.invokeExact<warning descr="One argument is expected">()</warning>;

    Test instance = new Test(123);
    Test tooManyArguments1 = (Test) handle.invokeWithArguments<warning descr="One argument is expected">(instance, 1)</warning>;
    Test tooManyArguments2 = (Test) handle.invoke<warning descr="One argument is expected">(instance, 2)</warning>;
    Test tooManyArguments3 = (Test) handle.invokeExact<warning descr="One argument is expected">(instance, 3)</warning>;
  }

  void fooStr() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle handle = lookup.findConstructor(Test.class, MethodType.methodType(void.class, String.class));

    Test exactSignature1 = (Test) handle.invokeWithArguments("a");
    Test exactSignature2 = (Test) handle.invoke("b");
    Test exactSignature3 = (Test) handle.invokeExact("c");

    Object object = "abc";
    Test objectArgument1 = (Test) handle.invokeWithArguments(object);
    Test objectArgument2 = (Test) handle.invoke(object);
    Test objectArgument3 = (Test) handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">object</warning>);

    Object objectResult = handle.invokeWithArguments("a");
    objectResult = handle.invoke("b");
    objectResult = <warning descr="Should be cast to 'Test'">handle.invokeExact</warning>("c");
    Object objectResult1 = <warning descr="Should be cast to 'Test'">handle.invokeExact</warning>("c");

    Test argumentsArray1 = (Test) handle.invokeWithArguments(new Object[]{"a"});
    Test argumentsArray2 = (Test) handle.invoke(<warning descr="Argument is not assignable to 'java.lang.String'">new Object[]{"b"}</warning>);
    Test argumentsArray3 = (Test) handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">new Object[]{"c"}</warning>);

    Test incompatibleArgument1 = (Test) handle.invokeWithArguments(<warning descr="Argument is not assignable to 'java.lang.String'">1</warning>);
    Test incompatibleArgument2 = (Test) handle.invoke(<warning descr="Argument is not assignable to 'java.lang.String'">2</warning>);
    Test incompatibleArgument3 = (Test) handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">3</warning>);

    int incompatibleResult1 = (<warning descr="Should be cast to 'Test' or its superclass">int</warning>) handle.invokeWithArguments("a");
    int incompatibleResult2 = (<warning descr="Should be cast to 'Test' or its superclass">int</warning>) handle.invoke("b");
    int incompatibleResult3 = (<warning descr="Should be cast to 'Test'">int</warning>) handle.invokeExact("c");

    Object nullArg = null;
    Test nullArgument1 = (Test) handle.invokeWithArguments(nullArg);
    Test nullArgument2 = (Test) handle.invoke(null);
    Test nullArgument3 = (Test) handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">null</warning>);

    Test tooFewArguments1 = (Test) handle.invokeWithArguments<warning descr="One argument is expected">()</warning>;
    Test tooFewArguments2 = (Test) handle.invoke<warning descr="One argument is expected">()</warning>;
    Test tooFewArguments3 = (Test) handle.invokeExact<warning descr="One argument is expected">()</warning>;

    Test instance = new Test("abc");
    Test tooManyArguments1 = (Test) handle.invokeWithArguments<warning descr="One argument is expected">(instance, "a")</warning>;
    Test tooManyArguments2 = (Test) handle.invoke<warning descr="One argument is expected">(instance, "b")</warning>;
    Test tooManyArguments3 = (Test) handle.invokeExact<warning descr="One argument is expected">(instance, "c")</warning>;

    Test superclassArgument1 = (Test) handle.invokeWithArguments(charSequence());
    Test superclassArgument2 = (Test) handle.invoke(charSequence());
    Test superclassArgument3 = (Test) handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">charSequence()</warning>);

    Super superclassResult1 = (Super) handle.invokeWithArguments("a");
    Super superclassResult2 = (Super) handle.invoke("b");
    Super superclassResult3 = (<warning descr="Should be cast to 'Test'">Super</warning>) handle.invokeExact("c");
  }

  private static CharSequence charSequence() { return "abc"; }
}

class Super {}

class Test extends Super {
  public Test(int n) {}
  public Test(String s) {}
}