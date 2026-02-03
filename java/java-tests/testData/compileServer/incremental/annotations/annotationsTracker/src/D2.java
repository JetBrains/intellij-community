import org.jetbrains.jps.builders.java.dependencyView.MockAnnotation;
import org.jetbrains.jps.builders.java.dependencyView.MockHierarchyAnnotation;

public class D2 {
  @MockAnnotation
  @MockHierarchyAnnotation
  public String field;

  public String method (Integer param) {
    return param.toString();
  }
}