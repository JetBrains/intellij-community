import java.util.Optional;

class SecondChoice {
  public void ors(Optional<String> p1, Optional<String> p2) {
    Optional<String> o = Optional.ofNullable(p1.orElseGet(p2::get));
  }
}