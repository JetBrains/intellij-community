// "Extract Set from comparison chain" "true-preview"

class NoNameClash {
  // An existing field. The name is already taken.
  private static final Set<String> CLI_SWITCHES = Set.of("--x");

  boolean test(String cliSwitch) {
    return <caret>cliSwitch.equals("--dlib") || cliSwitch.equals("--daemon");
  }
}