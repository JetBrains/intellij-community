import java.util.Optional;

class Demo {
  Optional<String> test(String s) {
    return s.opt<caret>
  }
}
