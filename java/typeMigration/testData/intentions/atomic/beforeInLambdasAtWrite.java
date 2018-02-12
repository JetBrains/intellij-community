// "Convert to atomic" "true"

import java.util.function.*;

public class InLambdas
{
  public void test()
  {
    int x = 0;
    // Also active at write point if it causes a compilation error
    Runnable r1 = () -> <caret>x++;
    Runnable r2 = () -> x+=2;
    Runnable r3 = () -> x*=2;
    Runnable r4 = () -> x = 5;
    System.out.println(x /= 3);
    IntSupplier s = () -> {
      return x *= 2;
    };
  }
}