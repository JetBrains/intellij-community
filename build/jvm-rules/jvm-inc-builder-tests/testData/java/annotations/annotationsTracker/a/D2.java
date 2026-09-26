import org.jetbrains.jps.dependency.test.MockAnnotation;
import org.jetbrains.jps.dependency.test.MockHierarchyAnnotation;

public class D2 {
  @MockAnnotation
  @MockHierarchyAnnotation
  public String field;

  public String method (Integer param) {
    return param.toString();
  }
}
