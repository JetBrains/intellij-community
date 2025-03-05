import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Test
{
  public static void main(String[] args)
  {
    Map<Long, Long> longLongMap = new HashMap();

    List<Map.Entry<Long,Long>>  entryListWithError = longLongMap.entrySet().stream()
      .sorted<error descr="'sorted(java.util.Comparator<? super java.util.Map.Entry<java.lang.Long,java.lang.Long>>)' in 'java.util.stream.Stream' cannot be applied to '(java.util.Comparator<T>)'">(Comparator.comparing(Map.Entry::getValue).reversed())</error>
      .limit(10)
      .collect(Collectors.toList());
    List<Map.Entry<Long,Long>>  entryListWithoutError = longLongMap.entrySet().stream()
      .sorted(Comparator.comparing(Map.Entry::getValue))
      .limit(10)
      .collect(Collectors.toList());
  }
}