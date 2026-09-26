import org.jetbrains.jps.dependency.test.MockAnnotation;
import org.jetbrains.jps.dependency.test.MockHierarchyAnnotation;

@MockAnnotation
@MockHierarchyAnnotation
public class D1 {
  public String field;

  public String method (Integer param) {
    return param.toString();
  }
}
