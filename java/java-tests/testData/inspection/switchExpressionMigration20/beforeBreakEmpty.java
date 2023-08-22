// "Replace with enhanced 'switch' statement" "true"

class A {
  private static void testSwitch(Integer c) {
    switch<caret> (c) {
      case -1:
      case null:
        break;
      case 1:
      case 2:
        System.out.println(" > 0");
        break;
      default:
        System.out.println(" > 2");
        break;
    }
  }
}