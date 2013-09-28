abstract class A<T, S extends T>
{
      abstract S bar();
      void foo(A<Cloneable[], ? extends Throwable[]> a)
      {
          int x = a.bar().length;
      }
}
