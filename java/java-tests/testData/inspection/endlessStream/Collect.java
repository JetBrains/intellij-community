import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class Collect {
  public static void main(String[] args) {
    new Random().ints().boxed().<warning descr="Non-short-circuit operation consumes the infinite stream">collect</warning>(Collectors.toList());
    new Random().doubles().boxed().<warning descr="Non-short-circuit operation consumes the infinite stream">collect</warning>(Collectors.toList());
    new Random().longs().boxed().<warning descr="Non-short-circuit operation consumes the infinite stream">collect</warning>(Collectors.toList());
  }
}