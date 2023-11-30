import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Stream;
class TestInspection
{
  public static Stream<String> foo2(Object o)
  {
    HashSet<String> set = new HashSet<>();
    return collect(o, set).stream();
  }
  public static Stream<String> foo(Object o)
  {
    return collect(o, new HashSet<>()).stream();
  }
  private static Collection<String> collect(Object o, Collection<String> result)
  {
    collectAll(o, result);
    return result;
  }
  private static void collectAll(Object o, Collection<String> result)
  {
    result.add("foo");
  }
}