import java.util.Optional;

class MainTest {
  public static void main(String[] <flown1111>args) {
    Optional<String> optional;
    if (args.length > 0) {
      optional = <flown11>Optional.ofNullable(<flown111>args[0]);
    } else {
      optional = <flown12>Optional.of(<flown121>"foo");
    }
    String val = <flown1>optional.orElse(<flown13>"xyz");
    System.out.println(<caret>val);
  }
}