import java.util.*;

class MyTest {

  public interface Child {}

  {
    List<Child> children = new ArrayList<>();
    children.stream().filter(Objects::nonNull)<caret>
  }

}