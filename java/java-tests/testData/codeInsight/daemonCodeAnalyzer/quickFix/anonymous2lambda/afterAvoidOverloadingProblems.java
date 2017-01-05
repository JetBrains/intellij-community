// "Replace with lambda" "true"

import java.util.function.Supplier;

class A {

  public <T> T  runReadAction( final Supplier<T> computation) {

    return null;
  }


  public <T, E extends Throwable> T  runReadAction( ThrowableComputable<T, E> computation) throws E {
    return null;
  }

  {
    runReadAction((Supplier<String>) () -> "");
  }
}

@FunctionalInterface
interface ThrowableComputable<T, E extends Throwable> {
  T compute() throws E;
}