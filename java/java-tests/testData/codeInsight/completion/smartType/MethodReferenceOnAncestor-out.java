import java.util.*;

class MyTest {

  public interface Parent {
    boolean hasFlag();
  }

  public interface Child extends Parent {}

  public static void main(String[] args) {
    List<Child> children = new ArrayList<>();
    children.stream().filter(Parent::hasFlag)<caret>
  }

}