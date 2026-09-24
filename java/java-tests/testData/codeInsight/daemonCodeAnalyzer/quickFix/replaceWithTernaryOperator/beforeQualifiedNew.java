// "Introduce variable and replace with 'jc != null ?:'" "true-preview"
import org.jetbrains.annotations.Nullable;

public class JC {
  class Inner {}

  // tracked by the dataflow analysis as a getter, but not pure, so it must not be called twice
  native @Nullable JC getJc();

  public void test() {
    System.out.println(getJc().new In<caret>ner());
  }
}
