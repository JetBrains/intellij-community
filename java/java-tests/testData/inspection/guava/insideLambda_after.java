import com.google.common.base.*;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import java.util.ArrayList;
import java.util.stream.Stream;

public class Main20 {
  void m() {
    Supplier<Object> doubleSupplier = () -> {
      Stream<String> fi = new ArrayList<String>().stream();
      return fi.map(input -> input.length());
    };

  }
}