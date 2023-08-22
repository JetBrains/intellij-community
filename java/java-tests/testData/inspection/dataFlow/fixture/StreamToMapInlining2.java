import java.util.*;
import java.util.stream.*;

class Test {
  // IDEA-246544
  public static void main(String[] args) {
    List<Float> decimals = new ArrayList<>(Arrays.asList(1.2f, 4.4f, 2.7f, 2.5f));
    HashMap<Integer, List<Float>> decimalsGroupedByTheirIntegerParts = decimals.stream().collect(Collectors.toMap(
      Float::intValue,
      Collections::singletonList,
      (subgroup1, subgroup2) -> Stream.concat(subgroup1.stream(), subgroup2.stream()).collect(Collectors.toList()),
      () -> new HashMap<>()
    ));
    if (decimalsGroupedByTheirIntegerParts.isEmpty()) {}

    decimals.clear();
    HashMap<Integer, List<Float>> decimalsGroupedByTheirIntegerParts2 = decimals.stream().collect(Collectors.toMap(
      Float::intValue,
      Collections::singletonList,
      (subgroup1, subgroup2) -> Stream.concat(subgroup1.stream(), subgroup2.stream()).collect(Collectors.toList()),
      () -> new HashMap<>()
    ));
    if (<warning descr="Condition 'decimalsGroupedByTheirIntegerParts2.isEmpty()' is always 'true'">decimalsGroupedByTheirIntegerParts2.isEmpty()</warning>) {}
  }
}