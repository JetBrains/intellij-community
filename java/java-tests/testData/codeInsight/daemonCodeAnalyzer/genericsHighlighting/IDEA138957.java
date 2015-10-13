import java.util.List;

class MyClass<T>
{
  public MyClass(Class<? extends T> type) {}

  public static void main(String[] args)
  {
    <error descr="Incompatible types. Found: 'MyClass<java.util.List>', required: 'MyClass<java.util.List<java.lang.String>>'">MyClass<List<String>> myClass = new MyClass<>(List.class);</error>
  }
}