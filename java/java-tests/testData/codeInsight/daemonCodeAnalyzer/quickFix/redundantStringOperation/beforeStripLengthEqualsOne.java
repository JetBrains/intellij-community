// "Use 'isBlank()' and remove redundant 'strip()' call" "false"

class Test {
  boolean validCase(String text) {
    return text.<caret>strip().length() == 1;
  }
}
