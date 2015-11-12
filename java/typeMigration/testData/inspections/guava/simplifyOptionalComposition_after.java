import java.util.Optional;

class SecondChoice {
  public void ors(Optional<String> p1, java.util.Optional<String> p2) {
    java.util.Optional<String> o = java.util.Optional.ofNullable(p1.orElseGet(p2::get));
  }
}