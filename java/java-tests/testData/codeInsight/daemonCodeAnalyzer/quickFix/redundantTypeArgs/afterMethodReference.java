// "Remove explicit type arguments of method reference" "true"
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Box<T>
{
  public T getValue()
  {
    return null;
  }

  void f(Stream<Box<String>> stream) {
    List<String> l3 = stream
      .map(Box::getValue)
      .collect(Collectors.toList());
  }
}