import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class ArrayVarHandle {
  void array() {
    VarHandle handle = MethodHandles.arrayElementVarHandle(Test[].class);
    Test[] array = new Test[]{new Test(), new Test(), new SubTest()};
    Test instance = new Test();

    Test exactGet = (Test)handle.get(array, 0);
    Super superGet = (Super) handle.get(array, 1);
    Object objectGet = handle.get(array, 2);

    Object missingIndexGet = handle.get<warning descr="2 arguments are expected">(array)</warning>;
    Object incompatibleReceiverGet = handle.get(<warning descr="Call receiver type is incompatible: 'Test[]' is expected">instance</warning>, 1);
    Object incompatibleIndexGet = handle.get(array, "abc");
    String incompatibleResultGet = (<warning descr="Should be cast to 'Test' or its superclass">String</warning>)handle.get(<warning descr="Call receiver type is incompatible: 'Test[]' is expected">instance</warning>, 1);

    handle.set(array, 0, instance);
    handle.set(array, 1, new SubTest());
    Super superItemSet = new Test();
    handle.set(array, 1, superItemSet);
    Object objectItemSet = new Test();
    handle.set(array, 2, objectItemSet);

    handle.set(array, instance, <warning descr="Argument is not assignable to 'Test'">0</warning>);
    handle.set<warning descr="3 arguments are expected">(array, instance)</warning>;
    handle.set(<warning descr="Call receiver type is incompatible: 'Test[]' is expected">instance</warning>, array, <warning descr="Argument is not assignable to 'Test'">0</warning>);
    handle.set<warning descr="3 arguments are expected">(array, 0)</warning>;
    handle.set(array, 0, <warning descr="Argument is not assignable to 'Test'">"abc"</warning>);
    handle.set<warning descr="3 arguments are expected">(<warning descr="Call receiver type is incompatible: 'Test[]' is expected">"abc"</warning>)</warning>;

    Test exactGAS = (Test)handle.getAndSet(array, 0, instance);
    SubTest subGAS = (SubTest)handle.getAndSet(array, 1, instance);
    Super superGAS = (Super)handle.getAndSet(array, 2, instance);
    Object objectGAS = handle.getAndSet(array, 0, instance);

    Object tooManyArgumentsGAS = handle.getAndSet<warning descr="3 arguments are expected">(array, 0, instance, 1)</warning>;
    Object wrongArgumentGAS = handle.getAndSet(array, instance, <warning descr="Argument is not assignable to 'Test'">0</warning>);
    Test tooFewArgumentsGAS = (Test)handle.getAndSet<warning descr="3 arguments are expected">(array, instance)</warning>;

    boolean exactCAS = handle.compareAndSet(array, 0, instance, new Test());
    boolean subCAS = handle.compareAndSet(array, 0, new SubTest(), instance);

    boolean tooManyArgumentsCAS = handle.compareAndSet<warning descr="4 arguments are expected">(array, 0, instance, new Test(), new Test())</warning>;
    boolean wrongArgumentCAS = handle.compareAndSet(array, instance, new Test(), <warning descr="Argument is not assignable to 'Test'">2</warning>);
    boolean tooFewArgumentsCAS = handle.compareAndSet<warning descr="4 arguments are expected">(array, 0, instance)</warning>;

    Test exactCAE = (Test)handle.compareAndExchange(array, 0, instance, new Test());
    Super superCAE = (Super)handle.compareAndExchange(array, 0, new SubTest(), instance);

    Test tooManyArgumentsCAE = (Test)handle.compareAndExchange<warning descr="4 arguments are expected">(array, 0, instance, new Test(), new Test())</warning>;
    Test wrongArgumentCAE = (Test)handle.compareAndExchange(array, instance, new Test(), <warning descr="Argument is not assignable to 'Test'">2</warning>);
    Test tooFewArgumentsCAE = (Test)handle.compareAndExchange<warning descr="4 arguments are expected">(array, 0, instance)</warning>;
  }

  void array2() {
    VarHandle handle = MethodHandles.arrayElementVarHandle(Test[][].class);
    Test[] array0 = new Test[1];
    Test[][] array = new Test[][]{new Test[1]};
    Test instance = new Test();

    Test[] exactGet = (Test[])handle.get(array, 0);
    Object incompatibleReceiverGet = handle.get(<warning descr="Call receiver type is incompatible: 'Test[][]' is expected">array0</warning>, 1);
    Test incompatibleResultGet = (<warning descr="Should be cast to 'Test[]' or its superclass">Test</warning>)handle.get(array, 1);

    handle.set(array, 0, array0);
    handle.set(array, 0, <warning descr="Argument is not assignable to 'Test[]'">instance</warning>);

    Test[] exactGAS = (Test[])handle.getAndSet(array, 0, array0);
    Object wrongArgumentGAS = handle.getAndSet(array, 0, <warning descr="Argument is not assignable to 'Test[]'">instance</warning>);

    boolean exactCAS = handle.compareAndSet(array, 0, array0, new Test[0]);
    boolean wrongArgumentCAS = handle.compareAndSet(array, 0, array0, <warning descr="Argument is not assignable to 'Test[]'">instance</warning>);

    Object exactCAE = (Object)handle.compareAndExchange(array, 0, new Test[0], array0);
    Test[] wrongArgumentCAE = (Test[])handle.compareAndExchange(array, 0, <warning descr="Argument is not assignable to 'Test[]'">instance</warning>, array0);
    Test wrongResultCAE = (<warning descr="Should be cast to 'Test[]' or its superclass">Test</warning>)handle.compareAndExchange(array, 0, array0, new Test[0]);
  }
}

class Super {}

class Test extends Super {}

class SubTest extends Test {}