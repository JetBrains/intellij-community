
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class JavaClass {
  {
    Map<String, String> key2name = new HashMap<>();
    List<BigDecimal> codes = Arrays.asList(BigDecimal.ONE, BigDecimal.TEN);
    List<String> codes1 = Arrays.asList("a", "b", "c");

    final List<BigDecimal> list = codes.stream().filter(key2name::<warning descr="'Map<String, String>' may not contain keys of type 'BigDecimal'">containsKey</warning>).collect(Collectors.toList());
    final List<String>    list1 = codes1.stream().filter(key2name::containsKey).collect(Collectors.toList());
  }
}