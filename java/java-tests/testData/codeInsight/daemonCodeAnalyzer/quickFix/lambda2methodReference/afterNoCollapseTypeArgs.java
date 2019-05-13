// "Replace lambda with method reference" "true"
import java.util.function.Function;

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
    foo((Function<Box<String>, String>) Box::getValue);
  }
  
  <K> void foo(Function<Box<K>, K> f){}
}