import org.jetbrains.annotations.*;

public class Test {
  public @Nullable String foo;
  
  public void test() {
    
    if (foo != null && foo.length() > 1) {
      System.out.println(foo.length());
    }
  }
}
