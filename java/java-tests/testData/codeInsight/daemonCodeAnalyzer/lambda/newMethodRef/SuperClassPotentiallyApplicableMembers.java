import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

interface Base {
  int getInt(String a);
  int getInt();

  void foo();
}

abstract class ABase implements Base {
  @Override
  public int getInt(String a) {
    return 0;
  }

  @Override
  public int getInt() {
    return 0;
  }
}

class Impl extends ABase {
  @Override
  public int getInt() {
    return 0;
  }

  @Override
  public void foo() {
    List<String> strs = Arrays.asList("one", "two");

    List<Integer> withMethodRef = strs
      .stream()
      .map(this::getInt)
      .collect( Collectors.toList());
  }
}