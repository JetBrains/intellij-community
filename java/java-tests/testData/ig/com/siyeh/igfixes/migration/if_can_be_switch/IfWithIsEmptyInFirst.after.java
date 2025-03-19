class Comments {

  public static void test2(String str) {
      <caret>switch (str) {
          case "":
              System.out.println(20);
              break;
          case "20":
              System.out.println(30);
              break;
          case "30":
              System.out.println(298);
              break;
          default:
              throw new IllegalStateException("Unexpected value: " + str);
      }
  }
}