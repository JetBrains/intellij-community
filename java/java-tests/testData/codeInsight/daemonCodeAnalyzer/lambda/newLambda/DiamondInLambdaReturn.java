import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  Set<String> collect(Stream<String> map){
    return map.collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
  }
}
