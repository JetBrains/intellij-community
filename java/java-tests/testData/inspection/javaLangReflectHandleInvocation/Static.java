import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

class Main {
  void fooInt() throws Throwable {
    MethodHandle handle = MethodHandles.lookup().findStatic(Test.class, "foo", MethodType.methodType(int.class, int.class));

    int exactSignature1 = (int) handle.invokeWithArguments(1);
    int exactSignature2 = (int) handle.invoke(2);
    int exactSignature3 = (int) handle.invokeExact(3);

    Object object = 123;
    int objectArgument1 = (int) handle.invokeWithArguments(object);
    int objectArgument2 = (int) handle.invoke(object);
    int objectArgument3 = (int) handle.invokeExact(<warning descr="Argument type should be exactly 'int'">object</warning>);

    Object objectResult = handle.invokeWithArguments(1);
    objectResult = handle.invoke(2);
    objectResult = <warning descr="Should be cast to 'int'">handle.invokeExact</warning>(3);
    Object objectResult1 = <warning descr="Should be cast to 'int'">handle.invokeExact</warning>(3);

    int argumentsArray1 = (int) handle.invokeWithArguments(new Object[]{1});
    int argumentsArray2 = (int) handle.invoke(<warning descr="Argument is not assignable to 'int'">new Object[]{2}</warning>);
    int argumentsArray3 = (int) handle.invokeExact(<warning descr="Argument type should be exactly 'int'">new Object[]{3}</warning>);

    int boxedArgument1 = (int) handle.invokeWithArguments(Integer.valueOf(1));
    int boxedArgument2 = (int) handle.invoke(Integer.valueOf(2));
    int boxedArgument3 = (int) handle.invokeExact(<warning descr="Argument type should be exactly 'int'">Integer.valueOf(3)</warning>);

    Integer boxedResult1 = (Integer) handle.invokeWithArguments(1);
    Integer boxedResult2 = (Integer) handle.invoke(2);
    Integer boxedResult3 = (<warning descr="Should be cast to 'int'">Integer</warning>) handle.invokeExact(3);

    int incompatibleArgument1 = (int) handle.invokeWithArguments(<warning descr="Argument is not assignable to 'int'">"a"</warning>);
    int incompatibleArgument2 = (int) handle.invoke(<warning descr="Argument is not assignable to 'int'">"b"</warning>);
    int incompatibleArgument3 = (int) handle.invokeExact(<warning descr="Argument type should be exactly 'int'">"c"</warning>);

    String incompatibleResult1 = (<warning descr="Should be cast to 'int'">String</warning>) handle.invokeWithArguments(1);
    String incompatibleResult2 = (<warning descr="Should be cast to 'int'">String</warning>) handle.invoke(2);
    String incompatibleResult3 = (<warning descr="Should be cast to 'int'">String</warning>) handle.invokeExact(3);

    Object nullArg = null;
    int nullArgument1 = (int) handle.invokeWithArguments(<warning descr="Argument of type 'int' cannot be 'null'">nullArg</warning>);
    int nullArgument2 = (int) handle.invoke(<warning descr="Argument is not assignable to 'int'">null</warning>);
    int nullArgument3 = (int) handle.invokeExact(<warning descr="Argument type should be exactly 'int'">null</warning>);

    int tooFewArguments1 = (int) handle.invokeWithArguments<warning descr="One argument is expected">()</warning>;
    int tooFewArguments2 = (int) handle.invoke<warning descr="One argument is expected">()</warning>;
    int tooFewArguments3 = (int) handle.invokeExact<warning descr="One argument is expected">()</warning>;

    Test instance = new Test();
    int tooManyArguments1 = (int) handle.invokeWithArguments<warning descr="One argument is expected">(instance, 1)</warning>;
    int tooManyArguments2 = (int) handle.invoke<warning descr="One argument is expected">(instance, 2)</warning>;
    int tooManyArguments3 = (int) handle.invokeExact<warning descr="One argument is expected">(instance, 3)</warning>;
  }

  void fooStr() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle handle = lookup.findStatic(Test.class, "foo", MethodType.methodType(String.class, String.class));

    String exactSignature1 = (String) handle.invokeWithArguments("a");
    String exactSignature2 = (String) handle.invoke("b");
    String exactSignature3 = (String) handle.invokeExact("c");

    Object object = "abc";
    String objectArgument1 = (String) handle.invokeWithArguments(object);
    String objectArgument2 = (String) handle.invoke(object);
    String objectArgument3 = (String) handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">object</warning>);

    Object objectResult = handle.invokeWithArguments("a");
    objectResult = handle.invoke("b");
    objectResult = <warning descr="Should be cast to 'java.lang.String'">handle.invokeExact</warning>("c");
    Object objectResult1 = <warning descr="Should be cast to 'java.lang.String'">handle.invokeExact</warning>("c");

    String argumentsArray1 = (String) handle.invokeWithArguments(new Object[]{"a"});
    String argumentsArray2 = (String) handle.invoke(<warning descr="Argument is not assignable to 'java.lang.String'">new Object[]{"b"}</warning>);
    String argumentsArray3 = (String) handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">new Object[]{"c"}</warning>);

    String incompatibleArgument1 = (String) handle.invokeWithArguments(<warning descr="Argument is not assignable to 'java.lang.String'">1</warning>);
    String incompatibleArgument2 = (String) handle.invoke(<warning descr="Argument is not assignable to 'java.lang.String'">2</warning>);
    String incompatibleArgument3 = (String) handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">3</warning>);

    int incompatibleResult1 = (<warning descr="Should be cast to 'java.lang.String' or its superclass">int</warning>) handle.invokeWithArguments("a");
    int incompatibleResult2 = (<warning descr="Should be cast to 'java.lang.String' or its superclass">int</warning>) handle.invoke("b");
    int incompatibleResult3 = (<warning descr="Should be cast to 'java.lang.String'">int</warning>) handle.invokeExact("c");

    Object nullArg = null;
    String nullArgument1 = (String) handle.invokeWithArguments(nullArg);
    String nullArgument2 = (String) handle.invoke(null);
    String nullArgument3 = (String) handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">null</warning>);

    String tooFewArguments1 = (String) handle.invokeWithArguments<warning descr="One argument is expected">()</warning>;
    String tooFewArguments2 = (String) handle.invoke<warning descr="One argument is expected">()</warning>;
    String tooFewArguments3 = (String) handle.invokeExact<warning descr="One argument is expected">()</warning>;

    Test instance = new Test();
    String tooManyArguments1 = (String) handle.invokeWithArguments<warning descr="One argument is expected">(instance, "a")</warning>;
    String tooManyArguments2 = (String) handle.invoke<warning descr="One argument is expected">(instance, "b")</warning>;
    String tooManyArguments3 = (String) handle.invokeExact<warning descr="One argument is expected">(instance, "c")</warning>;

    String superclassArgument1 = (String) handle.invokeWithArguments(charSequence());
    String superclassArgument2 = (String) handle.invoke(charSequence());
    String superclassArgument3 = (String) handle.invokeExact(<warning descr="Argument type should be exactly 'java.lang.String'">charSequence()</warning>);

    CharSequence superclassResult1 = (CharSequence) handle.invokeWithArguments("a");
    CharSequence superclassResult2 = (CharSequence) handle.invoke("b");
    CharSequence superclassResult3 = (<warning descr="Should be cast to 'java.lang.String'">CharSequence</warning>) handle.invokeExact("c");
  }

  private static CharSequence charSequence() { return "abc"; }
}

class Test {
  public static int foo(int n) {return n;}
  public static String foo(String s) {return s;}
}