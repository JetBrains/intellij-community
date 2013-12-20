final class Test
{
  public static class Foo
  {
    void fun()
    {

    }
  }

  public Foo get()
  {
    return new Foo()
    {
      @Override
      void fun()
      {
        super.fun();
      }
    };
  }
}
