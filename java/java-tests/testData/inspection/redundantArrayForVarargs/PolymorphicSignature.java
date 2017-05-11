import java.lang.invoke.MethodHandle;

public class PolymorphicSignature {

  public static void main(String[] args) throws Throwable {
    MethodHandle meh = null;
    meh.invokeExact(new Object[] {});
  }
}