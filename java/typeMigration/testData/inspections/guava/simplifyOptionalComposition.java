import com.google.common.base.Optional;

class SecondChoice {
  public void ors(Opti<caret>onal<String> p1, java.util.Optional<String> p2) {
    Optional o = p1.or(Optional.fromNullable(p2.orElse(null)));
  }
}