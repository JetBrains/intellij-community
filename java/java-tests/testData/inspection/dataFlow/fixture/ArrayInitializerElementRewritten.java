import org.jetbrains.annotations.*;

public class ArrayInitializerElementRewritten {
  public static void main(String[] args) throws InterruptedException {
    Object p = null;
    Object[] ps = new Object[]{p};
    p = new Object();
    if (ps[0] == null) {}
  }
}