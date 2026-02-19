import java.util.List;

public class UnusedCounts {

  record Record3(int i1, int i2, int i3) {

  }

  public static void test(List<Record3> record3List) {
      for (Record3(var i1, var i2, var i3) : record3List) {
          int t1 = i1;
          System.out.println(i1);
          System.out.println(i2);
          System.out.println(i3);
      }

      for (Record3(var i1, var i2, var i3) : record3List) {
          System.out.println(i1);
          System.out.println(i2);
      }

    for (Record3 record3 : record3List) {
      System.out.println(record3.i1);
    }
  }
}
