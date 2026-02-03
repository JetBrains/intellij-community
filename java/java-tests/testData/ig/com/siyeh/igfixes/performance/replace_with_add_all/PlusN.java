import java.util.*;

class Vector {
  private final List<Number> values;

  public Vector(int n, Number... args) {
    values = new ArrayList<>(args.length + 1);
    values.add(null);
        <caret>for (int i = n; i < args.length + n; ++i) {
      values.add(args[i - n]);
    }
  }
}
