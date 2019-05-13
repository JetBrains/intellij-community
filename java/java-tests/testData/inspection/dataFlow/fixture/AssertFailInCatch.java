import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

class Test {

  public static void foo() {
    String result = null;
    try {
      result = createString();
    }
    catch (Exception e) {
      Assert.fail();
    }
    finally {
      if (result == null) {
        System.out.println("Analysis failed!");
      }
    }
  }

  private static native @NotNull String createString();
}