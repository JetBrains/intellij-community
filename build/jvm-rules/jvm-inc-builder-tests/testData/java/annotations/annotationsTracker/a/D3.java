import org.jetbrains.jps.dependency.test.MockAnnotation;
import org.jetbrains.jps.dependency.test.MockHierarchyAnnotation;

public class D3 {
  public String field;

  @MockAnnotation
  @MockHierarchyAnnotation
  public String method (Integer param) {
    return param.toString();
  }
}
