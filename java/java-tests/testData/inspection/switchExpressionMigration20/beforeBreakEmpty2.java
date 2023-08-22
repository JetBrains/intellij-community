// "Replace with enhanced 'switch' statement" "true"

class A {
  private static void testSwitch2(Integer c) {
    switch<caret> (c) {
      case null:
      case -1:
        break;
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