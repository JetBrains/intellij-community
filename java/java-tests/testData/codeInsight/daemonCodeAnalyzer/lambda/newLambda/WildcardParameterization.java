import java.util.function.Function;

class Sample {
  {
    Function<? extends String, ? extends Integer> f = (String s)  -> s.length();
  }
}
