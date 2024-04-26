import org.jetbrains.annotations.NotNull;

public class Test {

  void test() {
      Result result = getResult();

      System.out.println("Point(" + result.x() + ", " + result.y() + ")");
  }

    private static @NotNull Result getResult() {
        int x = 0;
        int y = 0;
        System.out.println();
        Result result = new Result(x, y);
        return result;
    }

    private record Result(int x, int y) {
    }
}