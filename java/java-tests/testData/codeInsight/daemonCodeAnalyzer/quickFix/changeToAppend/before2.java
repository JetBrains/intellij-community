// "Change to 'builder.append(1+1).append(s).append("  ")'" "true-preview"
class Test {
  String s;
  void bar(StringBuilder builder) {
    buil<caret>der += 1 + 1 + s + "  ";
  }
}