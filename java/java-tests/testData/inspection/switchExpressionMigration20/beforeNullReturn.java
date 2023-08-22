// "Replace with enhanced 'switch' statement" "true"

class A {
  private int void testSwitch2(Integer c) {
    switch<caret> (c) {
      case null:
      case -1:
        return 1;
      case 0:
        return 0;
      case 1:
      case 2:
        return 3;
      default:
        return 5;
    }
  }
}