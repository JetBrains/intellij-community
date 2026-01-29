import org.jetbrains.jps.dependency.test.MockAnnotation;
import org.jetbrains.jps.dependency.test.MockHierarchyAnnotation;

public class D4 {
  public String field;

  public String method (@MockAnnotation @MockHierarchyAnnotation Integer param) {
    return param.toString();
  }
}
