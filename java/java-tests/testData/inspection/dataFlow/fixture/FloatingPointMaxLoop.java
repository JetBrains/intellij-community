import java.util.*;

class Test {

  public static void main(String[] args) {
    Random random = new Random();

    List<Integer> tmp = getTmp();

    double max = 0;

    for(Integer integer : tmp) {
      double d = random.nextDouble();

      d = Math.min(d, random.nextDouble());

      if(d > max && d > integer) {
        max = d;
      }
    }

    System.out.println(max);

    if (max > 0) {
      throw new UnsupportedOperationException("Can't reach");
    }
  }

  private static native List<Integer> getTmp();
}