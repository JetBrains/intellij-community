// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

class Test {

  String returnOfOrElseValue(String in) {
      if (in != null) return in;
      return "foo";
  }

  void assignmentOfOrElseValue(String in) {
      String out = "foo";
      if (in != null) out = in;
  }

}