// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

class Test {
  String notNullValueCheckIsRemoved(String in) {
    if (in == null) return "foo";
    return Optional.ofNullable<caret>(in).orElse("bar");
  }
}