import java.util.function.Supplier;

public class OptionSupply {
  public void ors(java.util.Optional<String> p1, Supplier<String> p3) {
    String v = p1.orElseGet(p3);
  }
}