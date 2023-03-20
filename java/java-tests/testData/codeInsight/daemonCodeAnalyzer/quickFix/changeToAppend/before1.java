// "Change to 'appendable.append(1)'" "true-preview"
class Test {
  void appendable(StringBuilder appendable) throws IOException {
    appendab<caret>le += 1;
  }
}