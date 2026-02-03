// "Replace with enhanced 'switch' statement" "true-preview"

class CaseDefaultNull {

  private static void test2(String r2) {
      switch (r2) {
          case "1" -> System.out.println("1");
          case "2" -> System.out.println("2");
          case null, default -> System.out.println("3");
      }
  }
}