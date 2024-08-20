// "Create 'default' branch" "true"
class X {
  int test(int i) {
    return switch(i) {
      case 1 -> 2;
        default -> throw new IllegalStateException("Unexpected value: " + i);
    };
  }
}