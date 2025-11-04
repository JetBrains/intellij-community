import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

@NullMarked
public class StreamFilterPatching {
  public static List<Integer> getIntegersMR(List<@Nullable Integer> integers) {
    return integers.stream().filter(Objects::nonNull).toList();
  }

  public static List<Integer> getIntegersMR2(List<@Nullable Integer> integers) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected">integers.stream().filter(Objects::isNull).toList()</warning>;
  }

  public static List<Integer> getIntegersLambda(List<@Nullable Integer> integers) {
    return integers.stream().filter(obj -> Objects.nonNull(obj)).toList();
  }

  public static List<Integer> getIntegersLambda2(List<@Nullable Integer> integers) {
    return <warning descr="Returning a class with nullable type arguments when a class with not-null type arguments is expected">integers.stream().filter(obj -> Objects.isNull(obj)).toList()</warning>;
  }

  public static List<Integer> getIntegersLambda3(List<@Nullable Integer> integers) {
    return integers.stream().filter(obj -> !Objects.isNull(obj)).toList();
  }

  public static List<Integer> getIntegersLambda4(List<@Nullable Integer> integers) {
    return integers.stream().filter(obj -> obj != null).toList();
  }

  public static List<Number> getIntegersLambda5(List<@Nullable Number> integers) {
    return integers.stream().filter(obj -> obj instanceof Integer).toList();
  }

  public static List<Number> getIntegersLambda5And(List<@Nullable Number> integers) {
    return integers.stream().filter(obj -> obj instanceof Integer i && i == 10).toList();
  }

  public static List<Number> getIntegersInstance(List<@Nullable Number> integers) {
    return integers.stream().filter(Integer.class::isInstance).toList();
  }
}
