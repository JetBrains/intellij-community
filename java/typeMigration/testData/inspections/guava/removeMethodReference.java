import com.google.common.base.Supplier;

public class OptionSupply {
  public void ors(java.util.Optional<String> p1, Suppl<caret>ier<String> p3) {
    String v = p1.orElseGet(p3::get);
  }
}