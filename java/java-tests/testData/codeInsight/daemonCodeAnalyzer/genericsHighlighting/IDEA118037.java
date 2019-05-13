final class Test
{
  class Foo
  {
    <T extends String> void foo(Class<T> clazz)
    {
      if (<error descr="Operator '==' cannot be applied to 'java.lang.Class<java.lang.Void>', 'java.lang.Class<T>'">Void.class == clazz</error>)
      {
        System.out.println("Yeah!");
      }
    }
  }
}

