import java.util.Optional;
import java.util.function.Supplier;

public class OptionSupply {
  public void ors(Optional<String> p1, Supplier<String> p3) {
    String v = p1.orElseGet(p3);
  }
}