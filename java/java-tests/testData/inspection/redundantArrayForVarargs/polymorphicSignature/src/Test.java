import java.lang.invoke.MethodHandle;

public class Test {

  public static void main(String[] args) {
    MethodHandle meh = null;
    meh.invokeExact(new Object[] {});
  }
}