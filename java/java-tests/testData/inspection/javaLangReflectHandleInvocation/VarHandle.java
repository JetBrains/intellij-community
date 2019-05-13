import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

class Main {
  void getInt() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    VarHandle handle = lookup.findVarHandle(Test.class, "n", int.class);
    Test instance = new Test();
    Object nullArg = null;

    int exact = (int) handle.get(instance);
    Integer boxed = (Integer) handle.get(instance);
    Object object = (Object) handle.get(instance);
    String incompatible = (<warning descr="Should be cast to 'int'">String</warning>) handle.get(instance);
    handle.get<warning descr="One argument is expected">(instance, 1)</warning>;
    handle.get<warning descr="One argument is expected">()</warning>;
    handle.get(<warning descr="Call receiver is 'null'">nullArg</warning>);
    handle.get(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>);

    int exactV = (int) handle.getVolatile(instance);
    Integer boxedV = (Integer) handle.getVolatile(instance); 
    Object objectV = (Object) handle.getVolatile(instance);
    String incompatibleV = (<warning descr="Should be cast to 'int'">String</warning>) handle.getVolatile(instance);
    handle.getVolatile<warning descr="One argument is expected">(instance, 1)</warning>;
    handle.getVolatile<warning descr="One argument is expected">()</warning>;
    handle.getVolatile(<warning descr="Call receiver is 'null'">nullArg</warning>);
    handle.getVolatile(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>);

    int exactO = (int) handle.getOpaque(instance);
    Integer boxedO = (Integer) handle.getOpaque(instance); 
    Object objectO = (Object) handle.getOpaque(instance);
    String incompatibleO = (<warning descr="Should be cast to 'int'">String</warning>) handle.getOpaque(instance);
    handle.getOpaque<warning descr="One argument is expected">(instance, 1)</warning>;
    handle.getOpaque<warning descr="One argument is expected">()</warning>;
    handle.getOpaque(<warning descr="Call receiver is 'null'">nullArg</warning>);
    handle.getOpaque(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>);

    int exactA = (int) handle.getAcquire(instance);
    Integer boxedA = (Integer) handle.getAcquire(instance); 
    Object objectA = (Object) handle.getAcquire(instance);
    String incompatibleA = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAcquire(instance);
    handle.getAcquire<warning descr="One argument is expected">(instance, 1)</warning>;
    handle.getAcquire<warning descr="One argument is expected">()</warning>;
    handle.getAcquire(<warning descr="Call receiver is 'null'">nullArg</warning>);
    handle.getAcquire(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>);
  }

  void setInt() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    VarHandle handle = lookup.findVarHandle(Test.class, "n", int.class);
    Test instance = new Test();
    Object nullArg = null;
    Object textArg = "abc";
    Object numberArg = 123;

    handle.set(instance, 1); 
    handle.set(instance, Integer.valueOf(2)); 
    handle.set(instance, numberArg);
    handle.set(instance, textArg);
    handle.set<warning descr="2 arguments are expected">(instance)</warning>;
    handle.set<warning descr="2 arguments are expected">()</warning>;
    handle.set(<warning descr="Call receiver is 'null'">nullArg</warning>, 123);
    handle.set(instance, <warning descr="Argument of type 'int' cannot be 'null'">nullArg</warning>);
    handle.set(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>, 42);

    handle.setVolatile(instance, 1); 
    handle.setVolatile(instance, Integer.valueOf(2)); 
    handle.setVolatile(instance, numberArg);
    handle.setVolatile(instance, textArg);
    handle.setVolatile<warning descr="2 arguments are expected">(instance)</warning>;
    handle.setVolatile<warning descr="2 arguments are expected">()</warning>;
    handle.setVolatile(<warning descr="Call receiver is 'null'">nullArg</warning>, 123);
    handle.setVolatile(instance, <warning descr="Argument of type 'int' cannot be 'null'">nullArg</warning>);
    handle.setVolatile(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>, 42);

    handle.setOpaque(instance, 1); 
    handle.setOpaque(instance, Integer.valueOf(2)); 
    handle.setOpaque(instance, numberArg);
    handle.setOpaque(instance, textArg);
    handle.setOpaque<warning descr="2 arguments are expected">(instance)</warning>;
    handle.setOpaque<warning descr="2 arguments are expected">()</warning>;
    handle.setOpaque(<warning descr="Call receiver is 'null'">nullArg</warning>, 123);
    handle.setOpaque(instance, <warning descr="Argument of type 'int' cannot be 'null'">nullArg</warning>);
    handle.setOpaque(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>, 42);

    handle.setRelease(instance, 1);
    handle.setRelease(instance, Integer.valueOf(2)); 
    handle.setRelease(instance, numberArg);
    handle.setRelease(instance, textArg);
    handle.setRelease<warning descr="2 arguments are expected">(instance)</warning>;
    handle.setRelease<warning descr="2 arguments are expected">()</warning>;
    handle.setRelease(<warning descr="Call receiver is 'null'">nullArg</warning>, 123);
    handle.setRelease(instance, <warning descr="Argument of type 'int' cannot be 'null'">nullArg</warning>);
    handle.setRelease(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>, 42);
  }

  void getStr() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    final VarHandle handle = lookup.findVarHandle(Test.class, "s", String.class);
    final Test instance = new Test();
    final Object nullArg = null;

    String exact = (String) handle.get(instance);
    CharSequence parent = (CharSequence) handle.get(instance);
    Object object = (Object) handle.get(instance);
    Integer incompatible = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.get(instance);
    handle.get<warning descr="One argument is expected">(instance, 1)</warning>;
    handle.get<warning descr="One argument is expected">()</warning>;
    handle.get(<warning descr="Call receiver is 'null'">nullArg</warning>);
    handle.get(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>);

    String exactV = (String) handle.getVolatile(instance);
    CharSequence parentV = (CharSequence) handle.getVolatile(instance);
    Object objectV = (Object) handle.getVolatile(instance);
    Integer incompatibleV = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.getVolatile(instance);
    handle.getVolatile<warning descr="One argument is expected">(instance, 1)</warning>;
    handle.getVolatile<warning descr="One argument is expected">()</warning>;
    handle.getVolatile(<warning descr="Call receiver is 'null'">nullArg</warning>);
    handle.getVolatile(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>);

    String exactO = (String) handle.getOpaque(instance);
    CharSequence parentO = (CharSequence) handle.getOpaque(instance);
    Object objectO = (Object) handle.getOpaque(instance);
    Integer incompatibleO = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.getOpaque(instance);
    handle.getOpaque<warning descr="One argument is expected">(instance, 1)</warning>;
    handle.getOpaque<warning descr="One argument is expected">()</warning>;
    handle.getOpaque(<warning descr="Call receiver is 'null'">nullArg</warning>);
    handle.getOpaque(<warning descr="Call receiver type is incompatible: 'Test' is expected">"abc"</warning>);

    String exactA = (String) handle.getAcquire(instance);
    CharSequence parentA = (CharSequence) handle.getAcquire(instance);
    Object objectA = (Object) handle.getAcquire(instance);
    Integer incompatibleA = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.getAcquire(instance);
    handle.getAcquire<warning descr="One argument is expected">(instance, 1)</warning>;
    handle.getAcquire<warning descr="One argument is expected">()</warning>;
    handle.getAcquire(<warning descr="Call receiver is 'null'">nullArg</warning>);
    handle.getAcquire(<warning descr="Call receiver type is incompatible: 'Test' is expected">"abc"</warning>);
  }

  void setStr() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    final VarHandle handle = lookup.findVarHandle(Test.class, "s", String.class);
    final Test instance = new Test();
    final Object nullArg = null;
    final Object textArg = "abc";
    final Object numberArg = 3;

    handle.set(instance, "a"); 
    handle.set(instance, charSequence()); 
    handle.set(instance, textArg);
    handle.set(instance, nullArg);
    handle.set(instance, numberArg);
    handle.set<warning descr="2 arguments are expected">(instance)</warning>;
    handle.set<warning descr="2 arguments are expected">()</warning>;
    handle.set<warning descr="2 arguments are expected">(<warning descr="Call receiver is 'null'">nullArg</warning>)</warning>;
    handle.set(<warning descr="Call receiver type is incompatible: 'Test' is expected">"abc"</warning>, "d");

    handle.setVolatile(instance, "a"); 
    handle.setVolatile(instance, charSequence()); 
    handle.setVolatile(instance, textArg);
    handle.setVolatile(instance, nullArg);
    handle.setVolatile(instance, numberArg);
    handle.setVolatile<warning descr="2 arguments are expected">(instance)</warning>;
    handle.setVolatile<warning descr="2 arguments are expected">()</warning>;
    handle.setVolatile<warning descr="2 arguments are expected">(<warning descr="Call receiver is 'null'">nullArg</warning>)</warning>;
    handle.setVolatile(<warning descr="Call receiver type is incompatible: 'Test' is expected">"abc"</warning>, "d");

    handle.setOpaque(instance, "a"); 
    handle.setOpaque(instance, charSequence()); 
    handle.setOpaque(instance, textArg);
    handle.setOpaque(instance, nullArg);
    handle.setOpaque(instance, numberArg);
    handle.setOpaque<warning descr="2 arguments are expected">(instance)</warning>;
    handle.setOpaque<warning descr="2 arguments are expected">()</warning>;
    handle.setOpaque<warning descr="2 arguments are expected">(<warning descr="Call receiver is 'null'">nullArg</warning>)</warning>;
    handle.setOpaque(<warning descr="Call receiver type is incompatible: 'Test' is expected">"abc"</warning>, "d");

    handle.setRelease(instance, "a"); 
    handle.setRelease(instance, charSequence()); 
    handle.setRelease(instance, textArg);
    handle.setRelease(instance, nullArg);
    handle.setRelease(instance, numberArg);
    handle.setRelease<warning descr="2 arguments are expected">(instance)</warning>;
    handle.setRelease<warning descr="2 arguments are expected">()</warning>;
    handle.setRelease<warning descr="2 arguments are expected">(<warning descr="Call receiver is 'null'">nullArg</warning>)</warning>;
    handle.setRelease(<warning descr="Call receiver type is incompatible: 'Test' is expected">"abc"</warning>, "d");
  }


  private void getAndAddInt() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    VarHandle handle = lookup.findVarHandle(Test.class, "n", int.class);
    Test instance = new Test();
    Object nullArg = null;

    int exact = (int) handle.getAndAdd(instance, 1);
    Integer boxed = (Integer) handle.getAndAdd(instance, 2);
    Object object = (Object) handle.getAndAdd(instance, 3);
    handle.getAndAdd(instance, 4);
    String incompatible = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndAdd(instance, 0);
    int incompatibleArg = (int) handle.getAndSet(instance, <warning descr="Argument is not assignable to 'int'">"abc"</warning>);
    handle.getAndAdd<warning descr="2 arguments are expected">(instance)</warning>;
    handle.getAndAdd<warning descr="2 arguments are expected">()</warning>;
    handle.getAndAdd(<warning descr="Call receiver is 'null'">nullArg</warning>, 0);
    int wrongReceiver = (int)handle.getAndAdd(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>, 42);

    int exactA = (int) handle.getAndAddAcquire(instance, 1); ;
    Integer boxedA = (Integer) handle.getAndAddAcquire(instance, 2);
    Object objectA = (Object) handle.getAndAddAcquire(instance, 3);
    handle.getAndAddAcquire(instance, 4);
    String incompatibleA = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndAddAcquire(instance, 0);
    int incompatibleArgA = (int) handle.getAndSetAcquire(instance, <warning descr="Argument is not assignable to 'int'">"abc"</warning>);
    handle.getAndAddAcquire<warning descr="2 arguments are expected">(instance)</warning>;
    handle.getAndAddAcquire<warning descr="2 arguments are expected">()</warning>;
    handle.getAndAddAcquire(<warning descr="Call receiver is 'null'">nullArg</warning>, 0);
    int <error descr="Variable 'wrongReceiver' is already defined in the scope">wrongReceiver</error> = (int)handle.getAndAddAcquire(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>, 42);

    int exactR = (int) handle.getAndAddRelease(instance, 1); ;
    Integer boxedR = (Integer) handle.getAndAddRelease(instance, 2);
    Object objectR = (Object) handle.getAndAddRelease(instance, 3);
    handle.getAndAddRelease(instance, 4);
    String incompatibleR = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndAdd(instance, 0);
    int incompatibleArgR = (int) handle.getAndSetRelease(instance, <warning descr="Argument is not assignable to 'int'">"abc"</warning>);
    handle.getAndAddRelease<warning descr="2 arguments are expected">(instance)</warning>;
    handle.getAndAddRelease<warning descr="2 arguments are expected">()</warning>;
    handle.getAndAddRelease(<warning descr="Call receiver is 'null'">nullArg</warning>, 0);
    int wrongReceiverR = (int)handle.getAndAddRelease(<warning descr="Call receiver type is incompatible: 'Test' is expected">123</warning>, 42);
  }

  private void getAndSetStr() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    final VarHandle handle = lookup.findVarHandle(Test.class, "s", String.class);
    final Test instance = new Test();
    final Object nullArg = null;

    String exact = (String) handle.getAndSet(instance, "a");
    CharSequence parent = (CharSequence) handle.getAndSet(instance, "b");
    Object object = (Object) handle.getAndSet(instance, "c");
    String subclassArg = (String) handle.getAndSet(instance, charSequence());
    String withNullArg = (String) handle.getAndSet(instance, nullArg);
    handle.getAndSet(instance, "d");
    Integer incompatible = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.getAndSet(instance, "e");
    String incompatibleArg = (String) handle.getAndSet(instance, <warning descr="Argument is not assignable to 'java.lang.String'">123</warning>);
    handle.getAndSet<warning descr="2 arguments are expected">(instance)</warning>;
    handle.getAndSet<warning descr="2 arguments are expected">()</warning>;
    handle.getAndSet(<warning descr="Call receiver is 'null'">nullArg</warning>, "abc");
    int wrongReceiver = (<warning descr="Should be cast to 'java.lang.String' or its superclass">int</warning>)handle.getAndSet(<warning descr="Call receiver type is incompatible: 'Test' is expected">"abc"</warning>, "e");

    String exactA = (String) handle.getAndSetAcquire(instance, "a");
    CharSequence parentA = (CharSequence) handle.getAndSetAcquire(instance, "b");
    Object objectA = (Object) handle.getAndSetAcquire(instance, "c");;
    String subclassArgA = (String) handle.getAndSetAcquire(instance, charSequence());
    String withNullArgA = (String) handle.getAndSetAcquire(instance, nullArg);
    handle.getAndSetAcquire(instance, "d");
    Integer incompatibleA = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.getAndSetAcquire(instance, "e");
    String incompatibleArgA = (String) handle.getAndSetAcquire(instance, <warning descr="Argument is not assignable to 'java.lang.String'">123</warning>);
    handle.getAndSetAcquire<warning descr="2 arguments are expected">(instance)</warning>;
    handle.getAndSetAcquire<warning descr="2 arguments are expected">()</warning>;
    handle.getAndSetAcquire(<warning descr="Call receiver is 'null'">nullArg</warning>, "abc");
    int wrongReceiverA = (<warning descr="Should be cast to 'java.lang.String' or its superclass">int</warning>)handle.getAndSetAcquire(<warning descr="Call receiver type is incompatible: 'Test' is expected">"abc"</warning>, "e");

    String exactR = (String) handle.getAndSetRelease(instance, "a");
    CharSequence parentR = (CharSequence) handle.getAndSetRelease(instance, "b");
    Object objectR = (Object) handle.getAndSetRelease(instance, "c");;
    String subclassArgR = (String) handle.getAndSetRelease(instance, charSequence());
    String withNullArgR = (String) handle.getAndSetRelease(instance, nullArg);
    handle.getAndSetRelease(instance, "d");
    Integer incompatibleR = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>) handle.getAndSetRelease(instance, "e");
    String incompatibleArgR = (String) handle.getAndSetRelease(instance, <warning descr="Argument is not assignable to 'java.lang.String'">123</warning>);
    handle.getAndSetRelease<warning descr="2 arguments are expected">(instance)</warning>;
    handle.getAndSetRelease<warning descr="2 arguments are expected">()</warning>;
    handle.getAndSetRelease(<warning descr="Call receiver is 'null'">nullArg</warning>, "abc");
    int wrongReceiverR = (<warning descr="Should be cast to 'java.lang.String' or its superclass">int</warning>)handle.getAndSetRelease(<warning descr="Call receiver type is incompatible: 'Test' is expected">"abc"</warning>, "e");
  }

  void getAndBitwise() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    VarHandle handle = lookup.findVarHandle(Test.class, "n", int.class);
    Test instance = new Test();

    int exactBitwiseAnd = (int) handle.getAndBitwiseAnd(instance, 1);
    int exactBitwiseAndA = (int) handle.getAndBitwiseAndAcquire(instance, 2);
    int exactBitwiseAndR = (int) handle.getAndBitwiseAndRelease(instance, 3);

    int wrongArgBitwiseAnd = (int) handle.getAndBitwiseAnd(instance, <warning descr="Argument is not assignable to 'int'">"a"</warning>);
    int wrongArgBitwiseAndA = (int) handle.getAndBitwiseAndAcquire(instance, <warning descr="Argument is not assignable to 'int'">"b"</warning>);
    int wrongArgBitwiseAndR = (int) handle.getAndBitwiseAndRelease(instance, <warning descr="Argument is not assignable to 'int'">"c"</warning>);

    String wrongResultBitwiseAnd = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseAnd(instance, 1);
    String wrongResultBitwiseAndA = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseAndAcquire(instance, 2);
    String wrongResultBitwiseAndR = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseAndRelease(instance, 3);

    int exactBitwiseOr = (int) handle.getAndBitwiseOr(instance, 1);
    int exactBitwiseOrA = (int) handle.getAndBitwiseOrAcquire(instance, 2);
    int exactBitwiseOrR = (int) handle.getAndBitwiseOrRelease(instance, 3);

    int wrongArgBitwiseOr = (int) handle.getAndBitwiseOr(instance, <warning descr="Argument is not assignable to 'int'">"a"</warning>);
    int wrongArgBitwiseOrA = (int) handle.getAndBitwiseOrAcquire(instance, <warning descr="Argument is not assignable to 'int'">"b"</warning>);
    int wrongArgBitwiseOrR = (int) handle.getAndBitwiseOrRelease(instance, <warning descr="Argument is not assignable to 'int'">"c"</warning>);

    String wrongResultBitwiseOr = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseOr(instance, 1);
    String wrongResultBitwiseOrA = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseOrAcquire(instance, 2);
    String wrongResultBitwiseOrR = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseOrRelease(instance, 3);

    int exactBitwiseXor = (int) handle.getAndBitwiseXor(instance, 1);
    int exactBitwiseXorA = (int) handle.getAndBitwiseXorAcquire(instance, 2);
    int exactBitwiseXorR = (int) handle.getAndBitwiseXorRelease(instance, 3);

    int wrongArgBitwiseXor = (int) handle.getAndBitwiseXor(instance, <warning descr="Argument is not assignable to 'int'">"a"</warning>);
    int wrongArgBitwiseXorA = (int) handle.getAndBitwiseXorAcquire(instance, <warning descr="Argument is not assignable to 'int'">"b"</warning>);
    int wrongArgBitwiseXorR = (int) handle.getAndBitwiseXorRelease(instance, <warning descr="Argument is not assignable to 'int'">"c"</warning>);

    String wrongResultBitwiseXor = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseXor(instance, 1);
    String wrongResultBitwiseXorA = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseXorAcquire(instance, 2);
    String wrongResultBitwiseXorR = (<warning descr="Should be cast to 'int'">String</warning>) handle.getAndBitwiseXorRelease(instance, 3);
  }


  void compare() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    final VarHandle handle = lookup.findVarHandle(Test.class, "s", String.class);
    final Test instance = new Test();

    boolean exactCAS = handle.compareAndSet(instance, "a", "b");
    String exactCAE = (String)handle.compareAndExchange(instance, "a", "b");
    String exactCAEA = (String)handle.compareAndExchangeAcquire(instance, "a", "b");
    String exactCAER = (String)handle.compareAndExchangeRelease(instance, "a", "b");

    boolean badArg2CAS = handle.compareAndSet(instance, <warning descr="Argument is not assignable to 'java.lang.String'">1</warning>, "b");
    String badArg2CAE = (String)handle.compareAndExchange(instance, <warning descr="Argument is not assignable to 'java.lang.String'">2</warning>, "b");
    String badArg2CAEa = (String)handle.compareAndExchangeAcquire(instance, <warning descr="Argument is not assignable to 'java.lang.String'">3</warning>, "b");
    String badArg2CAEr = (String)handle.compareAndExchangeRelease(instance, <warning descr="Argument is not assignable to 'java.lang.String'">4</warning>, "b");

    boolean badArg3CAS = handle.compareAndSet(instance, "a", <warning descr="Argument is not assignable to 'java.lang.String'">1</warning>);
    String badArg3CAE = (String)handle.compareAndExchange(instance, "a", <warning descr="Argument is not assignable to 'java.lang.String'">2</warning>);
    String badArg3CAEa = (String)handle.compareAndExchangeAcquire(instance, "a", <warning descr="Argument is not assignable to 'java.lang.String'">3</warning>);
    String badArg3CAEr = (String)handle.compareAndExchangeRelease(instance, "a", <warning descr="Argument is not assignable to 'java.lang.String'">4</warning>);

    Integer badResultCAE = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>)handle.compareAndExchange(instance, "a", "b");
    Integer badResultCAEa = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>)handle.compareAndExchangeAcquire(instance, "a", "b");
    Integer badResultCAEr = (<warning descr="Should be cast to 'java.lang.String' or its superclass">Integer</warning>)handle.compareAndExchangeRelease(instance, "a", "b");
  }

  void weakCompare() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    final VarHandle handle = lookup.findVarHandle(Test.class, "s", String.class);
    final Test instance = new Test();

    boolean exactCAS = handle.weakCompareAndSet(instance, "a", "b");
    boolean exactCASp = handle.weakCompareAndSetPlain(instance, "a", "b");
    boolean exactCASa = handle.weakCompareAndSetAcquire(instance, "a", "b");
    boolean exactCASr = handle.weakCompareAndSetRelease(instance, "a", "b");

    boolean badArg2CAS = handle.weakCompareAndSet(instance, <warning descr="Argument is not assignable to 'java.lang.String'">1</warning>, "b");
    boolean badArg2CASp = handle.weakCompareAndSetPlain(instance, <warning descr="Argument is not assignable to 'java.lang.String'">2</warning>, "b");
    boolean badArg2CASa = handle.weakCompareAndSetAcquire(instance, <warning descr="Argument is not assignable to 'java.lang.String'">3</warning>, "b");
    boolean badArg2CASr = handle.weakCompareAndSetRelease(instance, <warning descr="Argument is not assignable to 'java.lang.String'">4</warning>, "b");

    boolean badArg3CAS = handle.weakCompareAndSet(instance, "a", <warning descr="Argument is not assignable to 'java.lang.String'">1</warning>);
    boolean badArg3CASp = handle.weakCompareAndSetPlain(instance, "a", <warning descr="Argument is not assignable to 'java.lang.String'">2</warning>);
    boolean badArg3CASa = handle.weakCompareAndSetAcquire(instance, "a", <warning descr="Argument is not assignable to 'java.lang.String'">3</warning>);
    boolean badArg3CASr = handle.weakCompareAndSetRelease(instance, "a", <warning descr="Argument is not assignable to 'java.lang.String'">4</warning>);
  }

  private static CharSequence charSequence() { return "abc"; }
}

class Test {
  public int n;
  public String s;
}