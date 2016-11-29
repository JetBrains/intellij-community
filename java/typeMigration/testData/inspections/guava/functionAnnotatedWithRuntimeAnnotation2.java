import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import javax.annotations.Nullable;
import static com.google.common.collect.FluentIterable.from

import java.util.ArrayList;
import java.util.List;

class A {

  void m(List<String> l) {
    Function<String, String> function = new Function<String, String>() {
      @Nullable
      @Override
      public String apply(@Nullable String x) {
        return x;
      }
    };

    boolean strings = FluentIterable.from(l).transform(new Function<String, String>() {
      @Nullable
      @Override
      public String apply(@Nullable String x) {
        return x;
      }
    }).first().isPresent();
  }

}