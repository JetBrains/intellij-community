import com.google.common.base.Optional;
public class SubOrder {
  public void useArray(Opti<caret>onal<String>[] pa) {
    Optional<String> v = pa[0];
  }
}