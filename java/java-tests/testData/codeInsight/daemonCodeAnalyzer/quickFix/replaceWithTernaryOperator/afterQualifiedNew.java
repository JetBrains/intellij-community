// "Introduce variable and replace with 'jc != null ?:'" "true-preview"
import org.jetbrains.annotations.Nullable;

public class JC {
  class Inner {}

  // tracked by the dataflow analysis as a getter, but not pure, so it must not be called twice
  native @Nullable JC getJc();

  public void test() {
      JC jc = getJc();
      System.out.println(jc != null ? jc.new Inner() : null);
  }
}
