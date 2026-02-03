import com.google.common.base.Supplier;

public class OptionSupply {
  public void ors(java.util.Optional<String> p1, Supplier<String> p<caret>3) {
    String v = p1.orElseGet(p3::get);
  }
}