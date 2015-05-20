import org.jetbrains.annotations.Nullable;

class BrokenAlignment {

  {
    Runnable t = <warning descr="Dereference of 'getString()' may produce 'java.lang.NullPointerException'">getString()</warning>::length;
    Runnable t2 = BrokenAlignment::getStringStatic;
  }

  @Nullable
  private String getString() {
    return null;
  }
  @Nullable
  private static String getStringStatic() {
    return null;
  }
}