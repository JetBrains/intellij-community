import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

class Main {

  void fooInt() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle handle = lookup.findStaticGetter(Test.class, "n", int.class);

    int exactSignature1 = (int) handle.invokeWithArguments();
    int exactSignature2 = (int) handle.invoke();
    int exactSignature3 = (int) handle.invokeExact();

    Object objectResult = handle.invokeWithArguments();
    objectResult = handle.invoke();
    objectResult = <warning descr="Should be cast to 'int'">handle.invokeExact</warning>();
    Object objectResult1 = <warning descr="Should be cast to 'int'">handle.invokeExact</warning>();

    int argumentsArray1 = (int) handle.invokeWithArguments(new Object[]{});
    int argumentsArray2 = (int) handle.invoke<warning descr="No arguments are expected">(new Object[]{})</warning>;
    int argumentsArray3 = (int) handle.invokeExact<warning descr="No arguments are expected">(new Object[]{})</warning>;

    Integer boxedResult1 = (Integer) handle.invokeWithArguments();
    Integer boxedResult2 = (Integer) handle.invoke();
    Integer boxedResult3 = (<warning descr="Should be cast to 'int'">Integer</warning>) handle.invokeExact();

    String incompatibleResult1 = (<warning descr="Should be cast to 'int'">String</warning>) handle.invokeWithArguments();
    String incompatibleResult2 = (<warning descr="Should be cast to 'int'">String</warning>) handle.invoke();
    String incompatibleResult3 = (<warning descr="Should be cast to 'int'">String</warning>) handle.invokeExact();

    int tooManyArguments1 = (int) handle.invokeWithArguments<warning descr="No arguments are expected">(1, "a")</warning>;
    int tooManyArguments2 = (int) handle.invoke<warning descr="No arguments are expected">(2, "b")</warning>;
    int tooManyArguments3 = (int) handle.invokeExact<warning descr="No arguments are expected">(3, "c")</warning>;

    Test instance = new Test();
    int instanceArgument1 = (int) handle.invokeWithArguments<warning descr="No arguments are expected">(instance)</warning>;
    int instanceArgument2 = (int) handle.invoke<warning descr="No arguments are expected">(instance)</warning>;
    int instanceArgument3 = (int) handle.invokeExact<warning descr="No arguments are expected">(instance)</warning>;
  }

  void fooStr() throws Throwable {
    final MethodHandles.Lookup lookup = MethodHandles.lookup();
    final MethodHandle handle = lookup.findStaticGetter(Test.class, "s", String.class);

    String exactSignature1 = (String) handle.invokeWithArguments();
    String exactSignature2 = (String) handle.invoke();
    String exactSignature3 = (String) handle.invokeExact();

    Object objectResult = handle.invokeWithArguments();
    objectResult = handle.invoke();
    objectResult = <warning descr="Should be cast to 'java.lang.String'">handle.invokeExact</warning>();
    Object objectResult1 = <warning descr="Should be cast to 'java.lang.String'">handle.invokeExact</warning>();

    String argumentsArray1 = (String) handle.invokeWithArguments(new Object[]{});
    String argumentsArray2 = (String) handle.invoke<warning descr="No arguments are expected">(new Object[]{})</warning>;
    String argumentsArray3 = (String) handle.invokeExact<warning descr="No arguments are expected">(new Object[]{})</warning>;

    int incompatibleResult1 = (<warning descr="Should be cast to 'java.lang.String' or its superclass">int</warning>) handle.invokeWithArguments();
    int incompatibleResult2 = (<warning descr="Should be cast to 'java.lang.String' or its superclass">int</warning>) handle.invoke();
    int incompatibleResult3 = (<warning descr="Should be cast to 'java.lang.String'">int</warning>) handle.invokeExact();

    String tooManyArguments1 = (String) handle.invokeWithArguments<warning descr="No arguments are expected">("a", 1)</warning>;
    String tooManyArguments2 = (String) handle.invoke<warning descr="No arguments are expected">("b", 2)</warning>;
    String tooManyArguments3 = (String) handle.invokeExact<warning descr="No arguments are expected">("c", 3)</warning>;

    final Test instance = new Test();
    String instanceArgument1 = (String) handle.invokeWithArguments<warning descr="No arguments are expected">(instance)</warning>;
    String instanceArgument2 = (String) handle.invoke<warning descr="No arguments are expected">(instance)</warning>;
    String instanceArgument3 = (String) handle.invokeExact<warning descr="No arguments are expected">(instance)</warning>;

    CharSequence superclassResult1 = (CharSequence) handle.invokeWithArguments();
    CharSequence superclassResult2 = (CharSequence) handle.invoke();
    CharSequence superclassResult3 = (<warning descr="Should be cast to 'java.lang.String'">CharSequence</warning>) handle.invokeExact();
  }
}

class Super {}

class Test extends Super {
  public static int n;
  public static String s;
}