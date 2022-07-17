import java.util.Optional;

class Demo {
  Optional<String> test(String s) {
    return java.util.Optional.ofNullable(s)<caret>
  }
}
