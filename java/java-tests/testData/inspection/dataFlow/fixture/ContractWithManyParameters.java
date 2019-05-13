import java.util.Set;

class Foo {

  private static void calculate(String p1, String p2, Set<String> p3,
                                String p4, String p5,
                                String p6, Integer p7, Integer p8,
                                Integer p9, Boolean p10, String p11,
                                Integer p12, Integer p13) {
    validate(p1, p2, p4, p5, p6, p3.toString(), p7, p8, p9, p10, p11, p12, p13);
    System.out.println(p1);
  }

  public static void validate(String p1, String p2, String p3, String p4, String p5, String
    p6, Integer p7, Integer p8, Integer p9, Boolean p10, String p11, Integer p12, Integer p13) {
    if (p1 == null && p2 == null && p3 == null && p4 == null && p5 == null && p6 == null && p7 == null &&
        p8 == null && p9 == null && p10 == null && p11 == null && p12 == null && p13 == null)
      throw new RuntimeException();

    if (p10 != null && (p8 == null && p7 == null && p9 == null))
      throw new RuntimeException();

    if ((p12 != null || p13 != null) && (p12 == null || p13 == null))
      throw new RuntimeException();
  }


}
