import java.util.List;

public class UnusedCounts {

  record Record3(int i1, int i2, int i3) {

  }

  public static void test(List<Record3> record3List) {
    for (Record3 record3<caret> : record3List) {
      int t1 = record3.i1;
      System.out.println(record3.i1);
      System.out.println(record3.i2);
      System.out.println(record3.i3);
    }

    for (Record3 record3 : record3List) {
      System.out.println(record3.i1);
      System.out.println(record3.i2);
    }

    for (Record3 record3 : record3List) {
      System.out.println(record3.i1);
    }
  }
}
