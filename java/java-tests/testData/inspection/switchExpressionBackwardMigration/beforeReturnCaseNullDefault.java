// "Replace with old style 'switch' statement" "true"

class ReturnCaseNullDefault {
  int bar(Object o) {
    return switc<caret>h (o) {
      case null, default -> 1;
    }
  }
}