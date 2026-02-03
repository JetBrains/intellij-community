import java.util.Objects;

@javax.annotation.ParametersAreNonnullByDefault
public class Test {
  private final String message;

  public Test(String message) {
    this.message = Objects.requireNonNull(message);
  }
}