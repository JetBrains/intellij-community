import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

class SignaturePolymorphic {
  public static void main(String... args) throws Throwable {
    // signature polymorphic methods allow any lookup, do not warn
    MethodHandles.lookup().findVirtual(MethodHandle.class, "invoke", MethodType.methodType(int.class, String.class, double.class));
    MethodHandles.lookup().findVirtual(VarHandle.class, "compareAndSet", MethodType.methodType(int.class, String.class, double.class));
    // static lookup still warns as the methods are virtual
    MethodHandles.lookup().<warning descr="Method 'invoke' is not static">findStatic</warning>(MethodHandle.class, "invoke", MethodType.methodType(int.class, String.class, double.class));
    MethodHandles.lookup().<warning descr="Method 'compareAndSet' is not static">findStatic</warning>(VarHandle.class, "compareAndSet", MethodType.methodType(int.class, String.class, double.class));
    // unrelated methods in the relevant classes cause warnings
    MethodHandles.lookup().findVirtual(MethodHandle.class, "toString", <warning descr="Cannot resolve method 'int toString(String, double)'">MethodType.methodType(int.class, String.class, double.class)</warning>);
    MethodHandles.lookup().findVirtual(VarHandle.class, "toString", <warning descr="Cannot resolve method 'int toString(String, double)'">MethodType.methodType(int.class, String.class, double.class)</warning>);
  }
}
