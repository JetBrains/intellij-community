// "Change to 'builder.append(1+1).append(s).append("  ")'" "true"
class Test {
  String s;
  void bar(StringBuilder builder) {
    builder.append(1 + 1).append(s).append("  ");
  }
}