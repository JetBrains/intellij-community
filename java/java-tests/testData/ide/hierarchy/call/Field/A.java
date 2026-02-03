public class A {
  static String testField = "";

  public static void main1(String[] args) {
    testField += "";
  }

  public static void main2(String[] args) {
    testField += "";
    testField += "b";
  }

  public static void main3(String[] args) {
    testField += "";
  }

  public static void superMain(String[] args) {
    main1(args);
    main2(args);
    main3(args);
  }
}
