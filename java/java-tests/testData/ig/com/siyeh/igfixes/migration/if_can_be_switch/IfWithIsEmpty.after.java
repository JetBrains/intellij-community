class Comments {

  public static void test(String str) {
      <caret>switch (str) {
          case "10":
              System.out.println(20);
              break;
          case "20":
              System.out.println(30);
              break;
          case "":
              System.out.println(298);
              break;
          default:
              throw new IllegalStateException("Unexpected value: " + str);
      }
  }
}