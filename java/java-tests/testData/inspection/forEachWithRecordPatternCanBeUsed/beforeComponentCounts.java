import java.util.List;

public class ComponentCounts {

  record Record3(int i1, int i2, int i3) {

  }
  record Record4(int i1, int i2, int i3, int i4) {

  }

  public static void test(List<Record4> record4List, List<Record3> record3List) {
    for (Record4 record4 : record4List) {
      System.out.println(record4.i1);
      System.out.println(record4.i2);
      System.out.println(record4.i3);
      System.out.println(record4.i4);
    }

    for (Record3 record3<caret> : record3List) {
      System.out.println(record3.i1);
      System.out.println(record3.i2);
      System.out.println(record3.i3);
    }
  }
}
