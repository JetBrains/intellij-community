// "Replace with enhanced 'switch' statement" "true-preview"

class CaseDefaultNull {

  private static void test2(String r2) {
    swit<caret>ch (r2) {
      case "1":
        System.out.println("1");
        break;
      case "2":
        System.out.println("2");
        break;
      case null, default:
        System.out.println("3");
    }
  }
}