import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

class Main {

  void fooInt() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle handle = lookup.findGetter(Test.class, "n", int.class);
    Test instance = new Test();

    int exactSignature1 = (int) handle.invokeWithArguments(instance);
    int exactSignature2 = (int) handle.invoke(instance);
    int exactSignature3 = (int) handle.invokeExact(instance);

    Object objectResult = handle.invokeWithArguments(instance);
    objectResult = handle.invoke(instance);
    objectResult = <warning descr="Should be cast to 'int'">handle.invokeExact</warning>(instance);
    Object objectResult1 = <warning descr="Should be cast to 'int'">handle.invokeExact</warning>(instance);

    int argumentsArray1 = (int) handle.invokeWithArguments(new Object[]{instance});
    int argumentsArray2 = (int) handle.invoke(<warning descr="Call receiver type is incompatible: 'Test' is expected">new Object[]{instance}</warning>);
    int argumentsArray3 = (int) handle.invokeExact(<warning descr="Call receiver type is incompatible: 'Test' is expected">new Object[]{instance}</warning>);

    Integer boxedResult1 = (Integer) handle.invokeWithArguments(instance);
    Integer boxedResult2 = (Integer) handle.invoke(instance);
    Integer boxedResult3 = (<warning descr="Should be cast to 'int'">Integer</warning>) handle.invokeExact(instance);

    String incompatibleResult1 = (<warning descr="Should be cast to 'int'">String</warning>) handle.invokeWithArguments(instance);
    String incompatibleResult2 = (<warning descr="Should be cast to 'int'">String</warning>) handle.invoke(instance);
    String incompatibleResult3 = (<warning descr="Should be cast to 'int'">String</warning>) handle.invokeExact(instance);

    Object nullArg = null;
    int nullReceiver1 = (int) handle.invokeWithArguments(<warning descr="Call receiver is 'null'">nullArg</warning>);
    int nullReceiver2 = (int) handle.invoke(<warning descr="Call receiver is 'null'">nullArg</warning>);
    int nullReceiver3 = (int) handle.invokeExact(<warning descr="Call receiver is 'null'">nullArg</warning>);

    int incompatibleReceiver1 = (int) handle.invokeWithArguments(<warning descr="Call receiver type is incompatible: 'Test' is expected">42</warning>);
    int incompatibleReceiver2 = (int) handle.invoke(<warning descr="Call receiver type is incompatible: 'Test' is expected">42</warning>);
    int incompatibleReceiver3 = (int) handle.invokeExact(<warning descr="Call receiver type is incompatible: 'Test' is expected">42</warning>);

    int tooFewArguments1 = (int) handle.invokeWithArguments<warning descr="One argument is expected">()</warning>;
    int tooFewArguments2 = (int) handle.invoke<warning descr="One argument is expected">()</warning>;
    int tooFewArguments3 = (int) handle.invokeExact<warning descr="One argument is expected">()</warning>;

    int tooManyArguments1 = (int) handle.invokeWithArguments<warning descr="One argument is expected">(instance, 1)</warning>;
    int tooManyArguments2 = (int) handle.invoke<warning descr="One argument is expected">(instance, 2)</warning>;
    int tooManyArguments3 = (int) handle.invokeExact<warning descr="One argument is expected">(instance, 3)</warning>;
  }

  void fooStr() throws Throwable {
    final MethodHandles.Lookup lookup = MethodHandles.lookup();
    final MethodHandle handle = lookup.findGetter(Test.class, "s", String.class);
    final Test instance = new Test();

    String exactSignature1 = (String) handle.invokeWithArguments(instance);
    String exactSignature2 = (String) handle.invoke(instance);
    String exactSignature3 = (String) handle.invokeExact(instance);

    Object objectResult = handle.invokeWithArguments(instance);
    objectResult = handle.invoke(instance);
    objectResult = <warning descr="Should be cast to 'java.lang.String'">handle.invokeExact</warning>(instance);
    Object objectResult1 = <warning descr="Should be cast to 'java.lang.String'">handle.invokeExact</warning>(instance);

    String argumentsArray1 = (String) handle.invokeWithArguments(new Object[]{instance});
    String argumentsArray2 = (String) handle.invoke(<warning descr="Call receiver type is incompatible: 'Test' is expected">new Object[]{instance}</warning>);
    String argumentsArray3 = (String) handle.invokeExact(<warning descr="Call receiver type is incompatible: 'Test' is expected">new Object[]{instance}</warning>);

    int incompatibleResult1 = (<warning descr="Should be cast to 'java.lang.String' or its superclass">int</warning>) handle.invokeWithArguments(instance);
    int incompatibleResult2 = (<warning descr="Should be cast to 'java.lang.String' or its superclass">int</warning>) handle.invoke(instance);
    int incompatibleResult3 = (<warning descr="Should be cast to 'java.lang.String'">int</warning>) handle.invokeExact(instance);

    Object nullArg = null;
    String nullReceiver1 = (String) handle.invokeWithArguments(<warning descr="Call receiver is 'null'">nullArg</warning>);
    String nullReceiver2 = (String) handle.invoke(<warning descr="Call receiver is 'null'">nullArg</warning>);
    String nullReceiver3 = (String) handle.invokeExact(<warning descr="Call receiver is 'null'">nullArg</warning>);

    String incompatibleReceiver1 = (String) handle.invokeWithArguments(<warning descr="Call receiver type is incompatible: 'Test' is expected">"x"</warning>);
    String incompatibleReceiver2 = (String) handle.invoke(<warning descr="Call receiver type is incompatible: 'Test' is expected">"x"</warning>);
    String incompatibleReceiver3 = (String) handle.invokeExact(<warning descr="Call receiver type is incompatible: 'Test' is expected">"x"</warning>);

    String tooFewArguments1 = (String) handle.invokeWithArguments<warning descr="One argument is expected">()</warning>;
    String tooFewArguments2 = (String) handle.invoke<warning descr="One argument is expected">()</warning>;
    String tooFewArguments3 = (String) handle.invokeExact<warning descr="One argument is expected">()</warning>;

    String tooManyArguments1 = (String) handle.invokeWithArguments<warning descr="One argument is expected">(instance, 1)</warning>;
    String tooManyArguments2 = (String) handle.invoke<warning descr="One argument is expected">(instance, 2)</warning>;
    String tooManyArguments3 = (String) handle.invokeExact<warning descr="One argument is expected">(instance, 3)</warning>;

    CharSequence superclassResult1 = (CharSequence) handle.invokeWithArguments(instance);
    CharSequence superclassResult2 = (CharSequence) handle.invoke(instance);
    CharSequence superclassResult3 = (<warning descr="Should be cast to 'java.lang.String'">CharSequence</warning>) handle.invokeExact(instance);
  }
}

class Super {}

class Test extends Super {
  public int n;
  public String s;
}