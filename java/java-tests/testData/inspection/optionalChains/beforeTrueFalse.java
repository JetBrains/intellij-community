// "Simplify optional chain to '...isPresent()'" "true"
import java.util.Optional;

public class Test {
  interface Dto {
    int getId();
  }

  public void test(Dto dto) {
    boolean present = Optional.ofNullable(dto).map(Dto::getId).map(obj -> true).or<caret>Else(false);
  }
}