import com.google.common.collect.FluentIterable;

import java.util.Collection;

public class Main {

  public <T, C extends Collection<T>> C copyInto1(FluentIterable<T> fi, C collection) {
    return fi.copyInto(collection);
  }


  public <T, C extends Collection<? super T>> C copyInto2(FluentIterable<T> fi, C collection) {
    return fi.copyInto(collection);
  }
}