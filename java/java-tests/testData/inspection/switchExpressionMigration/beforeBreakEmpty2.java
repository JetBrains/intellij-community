// "Replace with enhanced 'switch' statement" "true-preview"

class A {
  private static void testSwitch2(int c) {
    switch<caret> (c) {
      case -2:
        break;
      case -1:
      case 0:
        break;
      case 1:
      case 2:
        System.out.println(" > 0");
        break;
      default:
        break;
    }
  }
}