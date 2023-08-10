import org.jetbrains.annotations.*;

public class GetterNullityAfterCheck {
  public String testGetter() {
    String something = getSomething();
    if (something != null) {
      return getSomething().trim();
    }
    return "";
  }
  
  native @Nullable String getSomething();
}