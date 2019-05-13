import java.util.Optional;
import java.util.function.Function;

class Scratch
{
  public static void main(String[] args) throws Exception
  {
    final Optional<Integer> i = foo();
    System.out.println(i);
  }

  private static Optional<Integer> foo()
  {
    final Optional<String> s = returnsR(
      "foo",
      z -> {
        if (z.isEmpty())
        {
          return Optional.empty();
        }
        else
        {
          return Optional.of("a string");
        }
      });

    if (s.isPresent())
    {
      return Optional.of(1);
    }
    else
    {
      return Optional.of(2);
    }
  }

  private static <R> R returnsR(String s, Function<String, R> f)
  {
    return f.apply(s);
  }
}