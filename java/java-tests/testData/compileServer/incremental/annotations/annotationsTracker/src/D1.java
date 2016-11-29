import org.jetbrains.jps.builders.java.dependencyView.MockAnnotation;
import org.jetbrains.jps.builders.java.dependencyView.MockHierarchyAnnotation;

@MockAnnotation
@MockHierarchyAnnotation
public class D1 {
  public String field;

  public String method (Integer param) {
    return param.toString();
  }
}