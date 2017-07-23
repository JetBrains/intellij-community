import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

class Main {
  void getInt() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    VarHandle handle = lookup.findStaticVarHandle(Test.class, "n", int.class);
    Test instance = new Test();

    int exact = (int) handle.get();
    Integer boxed = (Integer) handle.get();
    Object object = (Object) handle.get();
    String incompatible = (<warning descr="Should be cast to 'int'">String</warning>) handle.get();
    handle.get<warning descr="No arguments are expected">(instance)</warning>;

    int exactV = (int) handle.getVolatile();
    Integer boxedV = (Integer) handle.getVolatile();
    Object objectV = (Object) handle.getVolatile();
    String incompatibleV = (<warning descr="Should be cast to 'int'">String</warning>) handle.getVolatile();
    handle.getVolatile<warning descr="No arguments are expected">(instance)</warning>;

    int exactO = (int) handle.getOpaque();
    Integer boxedO = (Integer) handle.getOpaque();
    Object objectO = (Object) handle.getOpaque();
    String incompatibleO = (<warning descr="Should be cast to 'int'">String</warning>) handle.getOpaque();
    handle.getOpaque<warning descr="No arguments are expected">(instance)</warning>;

    int exactA = (int) handle.getAcquire();
    Integer boxedA = (Integer) handle.getAcquire();
    Object objectA = (Object) handle.getAcquire();
    String incompatibleA = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAcquire();
    handle.getAcquire<warning descr="No arguments are expected">(instance)</warning>;
  }

  void setInt() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    VarHandle handle = lookup.findStaticVarHandle(Test.class, "n", int.class);
    Test instance = new Test();
    Object nullArg = null;
    Object textArg = "abc";
    Object numberArg = 123;

    handle.set(1);
    handle.set(Integer.valueOf(2));
    handle.set(numberArg);
    handle.set(textArg);
    handle.set<warning descr="One argument is expected">(instance, 1)</warning>;
    handle.set(<warning descr="Argument of type 'int' cannot be 'null'">nullArg</warning>);
    handle.set<warning descr="One argument is expected">()</warning>;

    handle.setVolatile(1);
    handle.setVolatile(Integer.valueOf(2));
    handle.setVolatile(numberArg);
    handle.setVolatile(textArg);
    handle.setVolatile<warning descr="One argument is expected">(instance, 1)</warning>;
    handle.setVolatile(<warning descr="Argument of type 'int' cannot be 'null'">nullArg</warning>);
    handle.setVolatile<warning descr="One argument is expected">()</warning>;

    handle.setOpaque(1);
    handle.setOpaque(Integer.valueOf(2));
    handle.setOpaque(numberArg);
    handle.setOpaque(textArg);
    handle.setOpaque<warning descr="One argument is expected">(instance, 1)</warning>;
    handle.setOpaque(<warning descr="Argument of type 'int' cannot be 'null'">nullArg</warning>);
    handle.setOpaque<warning descr="One argument is expected">()</warning>;

    handle.setRelease(1);
    handle.setRelease(Integer.valueOf(2));
    handle.setRelease(numberArg);
    handle.setRelease(textArg);
    handle.setRelease<warning descr="One argument is expected">(instance, 1)</warning>;
    handle.setRelease(<warning descr="Argument of type 'int' cannot be 'null'">nullArg</warning>);
    handle.setRelease<warning descr="One argument is expected">()</warning>;
  }

  void getStr() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    final VarHandle handle = lookup.findStaticVarHandle(Test.class, "s", String.class);
    final Test instance = new Test();

    String exact = (String) handle.get();
    CharSequence parent = (CharSequence) handle.get();
    Object object = (Object) handle.get();
    Integer incompatible = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.get();
    handle.get<warning descr="No arguments are expected">(instance)</warning>;

    String exactV = (String) handle.getVolatile();
    CharSequence parentV = (CharSequence) handle.getVolatile();
    Object objectV = (Object) handle.getVolatile();
    Integer incompatibleV = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.getVolatile();
    handle.getVolatile<warning descr="No arguments are expected">(instance)</warning>;

    String exactO = (String) handle.getOpaque();
    CharSequence parentO = (CharSequence) handle.getOpaque();
    Object objectO = (Object) handle.getOpaque();
    Integer incompatibleO = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.getOpaque();
    handle.getOpaque<warning descr="No arguments are expected">(instance)</warning>;

    String exactA = (String) handle.getAcquire();
    CharSequence parentA = (CharSequence) handle.getAcquire();
    Object objectA = (Object) handle.getAcquire();
    Integer incompatibleA = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.getAcquire();
    handle.getAcquire<warning descr="No arguments are expected">(instance)</warning>;
  }

  void setStr() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    final VarHandle handle = lookup.findStaticVarHandle(Test.class, "s", String.class);
    final Test instance = new Test();
    final Object nullArg = null;
    final Object textArg = "abc";
    final Object numberArg = 3;

    handle.set("a");
    handle.set(charSequence());
    handle.set(textArg);
    handle.set(nullArg);
    handle.set(numberArg);
    handle.set<warning descr="One argument is expected">(instance, "abc")</warning>;
    handle.set<warning descr="One argument is expected">()</warning>;

    handle.setVolatile("a");
    handle.setVolatile(charSequence());
    handle.setVolatile(textArg);
    handle.setVolatile(nullArg);
    handle.setVolatile(numberArg);
    handle.setVolatile<warning descr="One argument is expected">(instance, "abc")</warning>;
    handle.setVolatile<warning descr="One argument is expected">()</warning>;

    handle.setOpaque("a");
    handle.setOpaque(charSequence());
    handle.setOpaque(textArg);
    handle.setOpaque(nullArg);
    handle.setOpaque(numberArg);
    handle.setOpaque<warning descr="One argument is expected">(instance, "abc")</warning>;
    handle.setOpaque<warning descr="One argument is expected">()</warning>;

    handle.setRelease("a");
    handle.setRelease(charSequence());
    handle.setRelease(textArg);
    handle.setRelease(nullArg);
    handle.setRelease(numberArg);
    handle.setRelease<warning descr="One argument is expected">(instance, "abc")</warning>;
    handle.setRelease<warning descr="One argument is expected">()</warning>;
  }


  private void getAndAddInt() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    VarHandle handle = lookup.findStaticVarHandle(Test.class, "n", int.class);
    Test instance = new Test();
    Object nullArg = null;

    int exact = (int) handle.getAndAdd(1);
    Integer boxed = (Integer) handle.getAndAdd(2);
    Object object = (Object) handle.getAndAdd(3);
    handle.getAndAdd(4);
    String incompatible = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndAdd(0);
    int incompatibleArg = (int) handle.getAndSet(<warning descr="Argument is not assignable to 'int'">"abc"</warning>);
    handle.getAndAdd<warning descr="One argument is expected">(1, 2)</warning>;
    handle.getAndAdd<warning descr="One argument is expected">()</warning>;

    int exactA = (int) handle.getAndAddAcquire(1); ;
    Integer boxedA = (Integer) handle.getAndAddAcquire(2);
    Object objectA = (Object) handle.getAndAddAcquire(3);
    handle.getAndAddAcquire(4);
    String incompatibleA = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndAddAcquire<warning descr="One argument is expected">(instance, 0)</warning>;
    int incompatibleArgA = (int) handle.getAndSetAcquire(<warning descr="Argument is not assignable to 'int'">"abc"</warning>);
    handle.getAndAddAcquire<warning descr="One argument is expected">(1, 2)</warning>;
    handle.getAndAddAcquire<warning descr="One argument is expected">()</warning>;

    int exactR = (int) handle.getAndAddRelease(1); ;
    Integer boxedR = (Integer) handle.getAndAddRelease(2);
    Object objectR = (Object) handle.getAndAddRelease(3);
    handle.getAndAddRelease(4);
    String incompatibleR = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndAdd(0);
    int incompatibleArgR = (int) handle.getAndSetRelease(<warning descr="Argument is not assignable to 'int'">"abc"</warning>);
    handle.getAndAddRelease<warning descr="One argument is expected">(1, 2)</warning>;
    handle.getAndAddRelease<warning descr="One argument is expected">()</warning>;
  }

  private void getAndSetStr() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    final VarHandle handle = lookup.findStaticVarHandle(Test.class, "s", String.class);
    final Test instance = new Test();
    final Object nullArg = null;

    String exact = (String) handle.getAndSet("a");
    CharSequence parent = (CharSequence) handle.getAndSet("b");
    Object object = (Object) handle.getAndSet("c");
    String subclassArg = (String) handle.getAndSet(charSequence());
    String withNullArg = (String) handle.getAndSet(nullArg);
    handle.getAndSet("d");
    Integer incompatible = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.getAndSet("e");
    String incompatibleArg = (String) handle.getAndSet(<warning descr="Argument is not assignable to 'java.lang.String'">123</warning>);
    handle.getAndSet<warning descr="One argument is expected">("a", "b")</warning>;
    handle.getAndSet<warning descr="One argument is expected">()</warning>;

    String exactA = (String) handle.getAndSetAcquire("a");
    CharSequence parentA = (CharSequence) handle.getAndSetAcquire("b");
    Object objectA = (Object) handle.getAndSetAcquire("c");;
    String subclassArgA = (String) handle.getAndSetAcquire(charSequence());
    String withNullArgA = (String) handle.getAndSetAcquire(nullArg);
    handle.getAndSetAcquire("d");
    Integer incompatibleA = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.getAndSetAcquire("e");
    String incompatibleArgA = (String) handle.getAndSetAcquire(<warning descr="Argument is not assignable to 'java.lang.String'">123</warning>);
    handle.getAndSetAcquire<warning descr="One argument is expected">("a", "b")</warning>;
    handle.getAndSetAcquire<warning descr="One argument is expected">()</warning>;

    String exactR = (String) handle.getAndSetRelease("a");
    CharSequence parentR = (CharSequence) handle.getAndSetRelease("b");
    Object objectR = (Object) handle.getAndSetRelease("c");;
    String subclassArgR = (String) handle.getAndSetRelease(charSequence());
    String withNullArgR = (String) handle.getAndSetRelease(nullArg);
    handle.getAndSetRelease("d");
    Integer incompatibleR = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.getAndSetRelease("e");
    String incompatibleArgR = (String) handle.getAndSetRelease(<warning descr="Argument is not assignable to 'java.lang.String'">123</warning>);
    handle.getAndSetRelease<warning descr="One argument is expected">("a", "b")</warning>;
    handle.getAndSetRelease<warning descr="One argument is expected">()</warning>;
  }

  void getAndBitwise() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    VarHandle handle = lookup.findStaticVarHandle(Test.class, "n", int.class);
    Test instance = new Test();

    int exactBitwiseAnd = (int) handle.getAndBitwiseAnd(1);
    int exactBitwiseAndA = (int) handle.getAndBitwiseAndAcquire(2);
    int exactBitwiseAndR = (int) handle.getAndBitwiseAndRelease(3);

    int wrongArgBitwiseAnd = (int) handle.getAndBitwiseAnd(<warning descr="Argument is not assignable to 'int'">"a"</warning>);
    int wrongArgBitwiseAndA = (int) handle.getAndBitwiseAndAcquire(<warning descr="Argument is not assignable to 'int'">"b"</warning>);
    int wrongArgBitwiseAndR = (int) handle.getAndBitwiseAndRelease(<warning descr="Argument is not assignable to 'int'">"c"</warning>);

    String wrongResultBitwiseAnd = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseAnd(1);
    String wrongResultBitwiseAndA = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseAndAcquire(2);
    String wrongResultBitwiseAndR = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseAndRelease(3);

    int exactBitwiseOr = (int) handle.getAndBitwiseOr(1);
    int exactBitwiseOrA = (int) handle.getAndBitwiseOrAcquire(2);
    int exactBitwiseOrR = (int) handle.getAndBitwiseOrRelease(3);

    int wrongArgBitwiseOr = (int) handle.getAndBitwiseOr(<warning descr="Argument is not assignable to 'int'">"a"</warning>);
    int wrongArgBitwiseOrA = (int) handle.getAndBitwiseOrAcquire(<warning descr="Argument is not assignable to 'int'">"b"</warning>);
    int wrongArgBitwiseOrR = (int) handle.getAndBitwiseOrRelease(<warning descr="Argument is not assignable to 'int'">"c"</warning>);

    String wrongResultBitwiseOr = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseOr(1);
    String wrongResultBitwiseOrA = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseOrAcquire(2);
    String wrongResultBitwiseOrR = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseOrRelease(3);

    int exactBitwiseXor = (int) handle.getAndBitwiseXor(1);
    int exactBitwiseXorA = (int) handle.getAndBitwiseXorAcquire(2);
    int exactBitwiseXorR = (int) handle.getAndBitwiseXorRelease(3);

    int wrongArgBitwiseXor = (int) handle.getAndBitwiseXor(<warning descr="Argument is not assignable to 'int'">"a"</warning>);
    int wrongArgBitwiseXorA = (int) handle.getAndBitwiseXorAcquire(<warning descr="Argument is not assignable to 'int'">"b"</warning>);
    int wrongArgBitwiseXorR = (int) handle.getAndBitwiseXorRelease(<warning descr="Argument is not assignable to 'int'">"c"</warning>);

    String wrongResultBitwiseXor = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseXor(1);
    String wrongResultBitwiseXorA = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseXorAcquire(2);
    String wrongResultBitwiseXorR = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseXorRelease(3);
  }


  void compare() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    final VarHandle handle = lookup.findStaticVarHandle(Test.class, "s", String.class);
    final Test instance = new Test();

    boolean exactCAS = handle.compareAndSet("a", "b");
    String exactCAE = (String)handle.compareAndExchange("a", "b");
    String exactCAEA = (String)handle.compareAndExchangeAcquire("a", "b");
    String exactCAER = (String)handle.compareAndExchangeRelease("a", "b");

    boolean badArg1CAS = handle.compareAndSet(<warning descr="Argument is not assignable to 'java.lang.String'">1</warning>, "b");
    String badArg1CAE = (String)handle.compareAndExchange(<warning descr="Argument is not assignable to 'java.lang.String'">2</warning>, "b");
    String badArg1CAEa = (String)handle.compareAndExchangeAcquire(<warning descr="Argument is not assignable to 'java.lang.String'">3</warning>, "b");
    String badArg1CAEr = (String)handle.compareAndExchangeRelease(<warning descr="Argument is not assignable to 'java.lang.String'">4</warning>, "b");

    boolean badArg2CAS = handle.compareAndSet("a", <warning descr="Argument is not assignable to 'java.lang.String'">1</warning>);
    String badArg2CAE = (String)handle.compareAndExchange("a", <warning descr="Argument is not assignable to 'java.lang.String'">2</warning>);
    String badArg2CAEa = (String)handle.compareAndExchangeAcquire("a", <warning descr="Argument is not assignable to 'java.lang.String'">3</warning>);
    String badArg2CAEr = (String)handle.compareAndExchangeRelease("a", <warning descr="Argument is not assignable to 'java.lang.String'">4</warning>);

    Integer badResultCAE = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>)handle.compareAndExchange("a", "b");
    Integer badResultCAEa = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>)handle.compareAndExchangeAcquire("a", "b");
    Integer badResultCAEr = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>)handle.compareAndExchangeRelease("a", "b");
  }

  void weakCompare() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    final VarHandle handle = lookup.findStaticVarHandle(Test.class, "s", String.class);
    final Test instance = new Test();

    boolean exactCAS = handle.weakCompareAndSet("a", "b");
    boolean exactCASp = handle.weakCompareAndSetPlain("a", "b");
    boolean exactCASa = handle.weakCompareAndSetAcquire("a", "b");
    boolean exactCASr = handle.weakCompareAndSetRelease("a", "b");

    boolean badArg1CAS = handle.weakCompareAndSet(<warning descr="Argument is not assignable to 'java.lang.String'">1</warning>, "b");
    boolean badArg1CASp = handle.weakCompareAndSetPlain(<warning descr="Argument is not assignable to 'java.lang.String'">2</warning>, "b");
    boolean badArg1CASa = handle.weakCompareAndSetAcquire(<warning descr="Argument is not assignable to 'java.lang.String'">3</warning>, "b");
    boolean badArg1CASr = handle.weakCompareAndSetRelease(<warning descr="Argument is not assignable to 'java.lang.String'">4</warning>, "b");

    boolean badArg2CAS = handle.weakCompareAndSet("a", <warning descr="Argument is not assignable to 'java.lang.String'">1</warning>);
    boolean badArg2CASp = handle.weakCompareAndSetPlain("a", <warning descr="Argument is not assignable to 'java.lang.String'">2</warning>);
    boolean badArg2CASa = handle.weakCompareAndSetAcquire("a", <warning descr="Argument is not assignable to 'java.lang.String'">3</warning>);
    boolean badArg2CASr = handle.weakCompareAndSetRelease("a", <warning descr="Argument is not assignable to 'java.lang.String'">4</warning>);
  }

  private static CharSequence charSequence() { return "abc"; }
}

class Test {
  public static int n;
  public static String s;
}