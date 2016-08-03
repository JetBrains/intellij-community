import org.hamcrest.CoreMatchers;
import org.jetbrains.annotations.Nullable;
import static org.junit.Assume.assumeThat;

class Contracts {

  private void checkNotNullValue(@Nullable Object o) {
    assumeThat(o, CoreMatchers.<Object>notNullValue());
    System.out.println(o.hashCode());
  }
}