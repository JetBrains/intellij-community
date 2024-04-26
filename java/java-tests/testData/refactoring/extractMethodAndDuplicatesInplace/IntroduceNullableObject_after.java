import org.jetbrains.annotations.Nullable;

public class Test {
  public static int main(boolean param) {
      Result result = getResult(param);
      if (result == null) return -1;

      System.out.println("Point(" + result.x() + ", " + result.y() + ")");
    return 0;
  }

    private static @Nullable Result getResult(boolean param) {
        int x = 0;
        int y = 0;
        if (param) return null;
        if (Math.random() > 0.5) return null;
        System.out.println();
        Result result = new Result(x, y);
        return result;
    }

    private record Result(int x, int y) {
    }
}