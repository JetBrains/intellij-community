import com.google.common.base.*;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import java.util.ArrayList;

public class Main20 {
  void m() {
    Supplier<Object> doubleSupplier = () -> {
      FluentIterable<String> fi = FluentIterable.from(new ArrayList<String>());
      return fi.transf<caret>orm(new Function<String, Integer>() {
        @Override
        public Integer apply(String input) {
          return input.length();
        }
      });
    };

  }
}