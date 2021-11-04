import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

class Main {
  void fooInt() throws Throwable {
    MethodHandle handle = MethodHandles.lookup().findVirtual(Test.class, "foo", MethodType.methodType(int.class, int.class));
    Test instance = new Test();

    int exactSignature1 = (int) handle.invokeWithArguments(instance, 1);
    int exactSignature2 = (int) handle.invoke(instance, 2);
    int exactSignature3 = (int) handle.invokeExact(instance, 3);

    Object object = 123;
    int objectArgument1 = (int) handle.invokeWithArguments(instance, object);
    int objectArgument2 = (int) handle.invoke(instance, object);
    int objectArgument3 = (int) handle.invokeExact(instance, <warning descr="Argument type should be exactly 'int'">object</warning>);

    Object objectResult = handle.invokeWithArguments(instance, 1);
    objectResult = handle.invoke(instance, 2);
    objectResult = <warning descr="Should be cast to 'int'">handle.invokeExact</warning>(instance, 3);
    Object objectResult1 = <warning descr="Should be cast to 'int'">handle.invokeExact</warning>(instance, 3);

    int argumentsArray1 = (int) handle.invokeWithArguments(new Object[]{instance, 1});
    int argumentsArray2 = (int) handle.invoke<warning descr="2 arguments are expected">(new Object[]{instance, 2})</warning>;
    int argumentsArray3 = (int) handle.invokeExact<warning descr="2 arguments are expected">(new Object[]{instance, 3})</warning>;

    int boxedArgument1 = (int) handle.invokeWithArguments(instance, Integer.valueOf(1));
    int boxedArgument2 = (int) handle.invoke(instance, Integer.valueOf(2));
    int boxedArgument3 = (int) handle.invokeExact(instance, <warning descr="Argument type should be exactly 'int'">Integer.valueOf(3)</warning>);

    Integer boxedResult1 = (Integer) handle.invokeWithArguments(instance, 1);
    Integer boxedResult2 = (Integer) handle.invoke(instance, 2);
    Integer boxedResult3 = (<warning descr="Should be cast to 'int'">Integer</warning>) handle.invokeExact(instance, 3);

    int incompatibleArgument1 = (int) handle.invokeWithArguments(instance,  <warning descr="Argument is not assignable to 'int'">"a"</warning>);
    int incompatibleArgument2 = (int) handle.invoke(instance, <warning descr="Argument is not assignable to 'int'">"b"</warning>);
    int incompatibleArgument3 = (int) handle.invokeExact(instance, <warning descr="Argument type should be exactly 'int'">"c"</warning>);

    String incompatibleResult1 = (<warning descr="Should be cast to 'int'">String</warning>) handle.invokeWithArguments(instance, 1);
    String incompatibleResult2 = (<warning descr="Should be cast to 'int'">String</warning>) handle.invoke(instance, 2);
    String incompatibleResult3 = (<warning descr="Should be cast to 'int'">String</warning>) handle.invokeExact(instance, 3);

    int nullReceiver1 = (int) handle.invokeWithArguments( <warning descr="Call receiver is 'null'">null</warning>, 1);
    int nullReceiver2 = (int) handle.invoke(<warning descr="Call receiver is 'null'">null</warning>, 2);
    int nullReceiver3 = (int) handle.invokeExact(<warning descr="Call receiver is 'null'">null</warning>, 3);

    int nullArgument1 = (int) handle.invokeWithArguments(instance, <warning descr="Argument is not assignable to 'int'">null</warning>);
    int nullArgument2 = (int) handle.invoke(instance, <warning descr="Argument is not assignable to 'int'">null</warning>);
    int nullArgument3 = (int) handle.invokeExact(instance, <warning descr="Argument type should be exactly 'int'">null</warning>);

    int incompatibleReceiver1 = (int) handle.invokeWithArguments(<warning descr="Call receiver type is incompatible: 'Test' is expected">42</warning>, 1);
    int incompatibleReceiver2 = (int) handle.invoke(<warning descr="Call receiver type is incompatible: 'Test' is expected">42</warning>, 2);
    int incompatibleReceiver3 = (int) handle.invokeExact(<warning descr="Call receiver type is incompatible: 'Test' is expected">42</warning>, 3);

    int tooFewArguments1 = (int) handle.invokeWithArguments<warning descr="2 arguments are expected">(1)</warning>;
    int tooFewArguments2 = (int) handle.invoke<warning descr="2 arguments are expected">(2)</warning>;
    int tooFewArguments3 = (int) handle.invokeExact<warning descr="2 arguments are expected">(3)</warning>;

    int tooManyArguments1 = (int) handle.invokeWithArguments<warning descr="2 arguments are expected">(instance, 1, "a")</warning>;
    int tooManyArguments2 = (int) handle.invoke<warning descr="2 arguments are expected">(instance, 2, "b")</warning>;
    int tooManyArguments3 = (int) handle.invokeExact<warning descr="2 arguments are expected">(instance, 3, "c")</warning>;
  }

  void fooStr() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle handle = lookup.findVirtual(Test.class, "foo", MethodType.methodType(String.class, String.class));
    Test instance = new Test();

    String exactSignature1 = (String) handle.invokeWithArguments(instance, "a");
    String exactSignature2 = (String) handle.invoke(instance, "b");
    String exactSignature3 = (String) handle.invokeExact(instance, "c");

    Object object = "abc";
    String objectArgument1 = (String) handle.invokeWithArguments(instance, object);
    String objectArgument2 = (String) handle.invoke(instance, object);
    String objectArgument3 = (String) handle.invokeExact(instance, <warning descr="Argument type should be exactly 'java.lang.String'">object</warning>);

    Object objectResult = handle.invokeWithArguments(instance, "a");
    objectResult = handle.invoke(instance, "b");
    objectResult = <warning descr="Should be cast to 'java.lang.String'">handle.invokeExact</warning>(instance, "c");
    Object objectResult1 = <warning descr="Should be cast to 'java.lang.String'">handle.invokeExact</warning>(instance, "c");

    String argumentsArray1 = (String) handle.invokeWithArguments(new Object[]{instance, "a"});
    String argumentsArray2 = (String) handle.invoke<warning descr="2 arguments are expected">(new Object[]{instance, "b"})</warning>;
    String argumentsArray3 = (String) handle.invokeExact<warning descr="2 arguments are expected">(new Object[]{instance, "c"})</warning>;

    String incompatibleArgument1 = (String) handle.invokeWithArguments(instance, <warning descr="Argument is not assignable to 'java.lang.String'">1</warning>);
    String incompatibleArgument2 = (String) handle.invoke(instance, <warning descr="Argument is not assignable to 'java.lang.String'">2</warning>);
    String incompatibleArgument3 = (String) handle.invokeExact(instance, <warning descr="Argument type should be exactly 'java.lang.String'">3</warning>);

    int incompatibleResult1 = (<warning descr="Should be cast to 'java.lang.String' or its superclass">int</warning>) handle.invokeWithArguments(instance, "a");
    int incompatibleResult2 = (<warning descr="Should be cast to 'java.lang.String' or its superclass">int</warning>) handle.invoke(instance, "b");
    int incompatibleResult3 = (<warning descr="Should be cast to 'java.lang.String'">int</warning>) handle.invokeExact(instance, "c");

    String nullReceiver1 = (String) handle.invokeWithArguments(<warning descr="Call receiver is 'null'">null</warning>, "a");
    String nullReceiver2 = (String) handle.invoke(<warning descr="Call receiver is 'null'">null</warning>, "b");
    String nullReceiver3 = (String) handle.invokeExact(<warning descr="Call receiver is 'null'">null</warning>, "c");

    String nullArgument1 = (String) handle.invokeWithArguments(instance, null);
    String nullArgument2 = (String) handle.invoke(instance, null);
    String nullArgument3 = (String) handle.invokeExact(instance, <warning descr="Argument type should be exactly 'java.lang.String'">null</warning>);

    String incompatibleReceiver1 = (String) handle.invokeWithArguments(<warning descr="Call receiver type is incompatible: 'Test' is expected">"x"</warning>, "a");
    String incompatibleReceiver2 = (String) handle.invoke(<warning descr="Call receiver type is incompatible: 'Test' is expected">"x"</warning>, "b");
    String incompatibleReceiver3 = (String) handle.invokeExact(<warning descr="Call receiver type is incompatible: 'Test' is expected">"x"</warning>, "c");

    String tooFewArguments1 = (String) handle.invokeWithArguments<warning descr="2 arguments are expected">("a")</warning>;
    String tooFewArguments2 = (String) handle.invoke<warning descr="2 arguments are expected">("b")</warning>;
    String tooFewArguments3 = (String) handle.invokeExact<warning descr="2 arguments are expected">("c")</warning>;

    String tooManyArguments1 = (String) handle.invokeWithArguments<warning descr="2 arguments are expected">(instance, "a", 1)</warning>;
    String tooManyArguments2 = (String) handle.invoke<warning descr="2 arguments are expected">(instance, "b", 2)</warning>;
    String tooManyArguments3 = (String) handle.invokeExact<warning descr="2 arguments are expected">(instance, "c", 3)</warning>;

    String superclassArgument1 = (String) handle.invokeWithArguments(instance, charSequence());
    String superclassArgument2 = (String) handle.invoke(instance, charSequence());
    String superclassArgument3 = (String) handle.invokeExact(instance, <warning descr="Argument type should be exactly 'java.lang.String'">charSequence()</warning>);

    CharSequence superclassResult1 = (CharSequence) handle.invokeWithArguments(instance, "a");
    CharSequence superclassResult2 = (CharSequence) handle.invoke(instance, "b");
    CharSequence superclassResult3 = (<warning descr="Should be cast to 'java.lang.String'">CharSequence</warning>) handle.invokeExact(instance, "c");
  }

  void fooVariousTypesVariousMethodTypes() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    Test instance = new Test();

    MethodType methodTypeFromArraysAsList = MethodType.methodType(long.class, Arrays.asList(int.class, long.class));
    MethodHandle methodHandle0 = lookup.findVirtual(Test.class, "foo", methodTypeFromArraysAsList);
    methodHandle0.invoke<warning descr="3 arguments are expected">(instance, 1, 2, 3)</warning>;

    MethodType methodTypeFromListOf = MethodType.methodType(long.class, (List.of(int.class, long.class)));
    MethodHandle methodHandle1 = lookup.findVirtual(Test.class, "foo", methodTypeFromListOf);
    methodHandle1.invoke<warning descr="3 arguments are expected">(instance, 1, 2, 3)</warning>;

    MethodHandle methodHandle2 = lookup.findVirtual(Test.class, "foo", MethodType.methodType(long.class, MethodType.methodType(Object.class, int.class, long.class)));
    methodHandle2.invoke<warning descr="3 arguments are expected">(instance, 1, 2, 3)</warning>;

    MethodHandle methodHandle3 = lookup.findVirtual(Test.class, "foo", MethodType.methodType(long.class, new Class<?>[]{int.class, long.class}));
    methodHandle3.invoke<warning descr="3 arguments are expected">(instance, 1, 2, 3)</warning>;

    MethodHandle methodHandle4 = lookup.findVirtual(Test.class, "foo", MethodType.methodType(long.class, new Class[]{int.class, long.class}));
    methodHandle4.invoke<warning descr="3 arguments are expected">(instance, 1, 2, 3)</warning>;

    MethodType nestedMethodType5 = MethodType.methodType(long.class, new Class[]{int.class, long.class});
    MethodHandle methodHandle5 = lookup.findVirtual(Test.class, "foo", MethodType.methodType(long.class, nestedMethodType5));
    methodHandle5.invoke<warning descr="3 arguments are expected">(instance, 1, 2, 3)</warning>;

    List<Class<?>> noWarningsForMutableLists = Arrays.asList(int.class, String.class);
    noWarningsForMutableLists.set(1, long.class);
    MethodHandle methodHandle6 = lookup.findVirtual(Test.class, "foo", MethodType.methodType(long.class, noWarningsForMutableLists));
    methodHandle6.invoke(instance, 1, 2L);

    MethodType noStackOverflowError = MethodType.methodType(long.class, <error descr="Variable 'noStackOverflowError' might not have been initialized">noStackOverflowError</error>);
    MethodHandle methodHandle7 = lookup.findVirtual(Test.class, "foo", noStackOverflowError);
    methodHandle7.invoke(instance, 1, 2L);
  }

  private static CharSequence charSequence() { return "abc"; }
}

class Test {
  public int foo(int n) {return n;}
  public String foo(String s) {return s;}
  public long foo(int i, long l) {
    return l + i;
  }
}
