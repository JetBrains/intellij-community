// "Replace lambda with method reference" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class Box<T>
{
  public Box(T value)
  {
    this._value = value;
  }

  private final T _value;

  public T getValue()
  {
    return this._value;
  }

  {
    List<Box<String>> l1 = new ArrayList<>();
    l1.add(new Box<>("Foo"));
    l1.add(new Box<>("Bar"));

    List<String> l3 = l1.stream()
      .map(Box::getValue)
      .collect(Collectors.toList());
  }
}