// "Replace with enhanced 'switch' statement" "true"

class A {
  private static void testSwitch2(Integer c) {
    int i;
    switch<caret> (c) {
      case null:
      case -1:
        i = 1;
        break;
      case 0:
        i = 0;
        break;
      case 1:
      case 2:
        i = 3;
        break;
      default:
        i = 5;
        break;
    }
  }
}