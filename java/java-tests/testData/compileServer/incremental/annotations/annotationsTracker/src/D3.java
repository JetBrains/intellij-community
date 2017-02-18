import org.jetbrains.jps.builders.java.dependencyView.MockAnnotation;
import org.jetbrains.jps.builders.java.dependencyView.MockHierarchyAnnotation;

public class D3 {
  public String field;

  @MockAnnotation
  @MockHierarchyAnnotation
  public String method (Integer param) {
    return param.toString();
  }
}