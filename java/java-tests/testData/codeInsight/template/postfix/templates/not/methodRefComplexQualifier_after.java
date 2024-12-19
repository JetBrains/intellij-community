import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public class Test {
  public static void main(String[] args) {
    BooleanSupplier p = () -> Stream.of(args).findFirst().isPresent()<caret>;
  }
}