// "Create 'default' branch" "true"
class X {
  int test(int i) {
    return switch(<caret>i) {
      case 1 -> 2;
    };
  }
}