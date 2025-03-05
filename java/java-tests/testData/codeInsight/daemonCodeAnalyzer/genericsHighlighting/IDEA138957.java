import java.util.List;

class MyClass<T>
{
  public MyClass(Class<? extends T> type) {}

  public static void main(String[] args)
  {
    MyClass<List<String>> myClass = new <error descr="Incompatible types. Found: 'MyClass<java.util.List>', required: 'MyClass<java.util.List<java.lang.String>>'">MyClass<></error>(List.class);
  }
}