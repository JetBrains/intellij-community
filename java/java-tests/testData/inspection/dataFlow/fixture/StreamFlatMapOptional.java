import java.util.Optional;
import java.util.stream.Stream;

public class StreamFlatMapOptional {

  public static void main(String[] args) {
    boolean foo = <warning descr="Result of 'Stream.of(\"foo\") .flatMap(elem -> Optional.of(false).stream()) .allMatch(Boolean::boolea...' is always 'false'">Stream.of("foo")
      .flatMap(elem -> Optional.of(false).stream())
      .allMatch(<warning descr="Method reference result is always 'false'">Boolean::booleanValue</warning>)</warning>;
    System.out.println(foo);
  }

}