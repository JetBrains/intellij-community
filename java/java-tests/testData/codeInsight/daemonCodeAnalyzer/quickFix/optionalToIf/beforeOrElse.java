// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

class Test {

  String returnOfOrElseValue(String in) {
    return Optional.ofNullable<caret>(in).orElse("foo");
  }

  void assignmentOfOrElseValue(String in) {
    String out = Optional.ofNullable(in).orElse("foo");
  }

}