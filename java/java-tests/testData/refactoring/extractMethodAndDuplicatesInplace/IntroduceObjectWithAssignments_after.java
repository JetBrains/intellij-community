import org.jetbrains.annotations.NotNull;

public class Test {
  public static void main(String[] args) {
      Result result = getResult();
      int x;
      x = (result.x() + result.y())/2;

    System.out.println("Point(" + x + ", " + result.y() + ")");
  }

    private static @NotNull Result getResult() {
        int x = 42;
        int y = 0;
        System.out.println();
        System.out.println();
        Result result = new Result(x, y);
        return result;
    }

    private record Result(int x, int y) {
    }
}