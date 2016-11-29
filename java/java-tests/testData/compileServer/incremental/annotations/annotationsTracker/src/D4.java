import org.jetbrains.jps.builders.java.dependencyView.MockAnnotation;
import org.jetbrains.jps.builders.java.dependencyView.MockHierarchyAnnotation;

public class D4 {
  public String field;

  public String method (@MockAnnotation @MockHierarchyAnnotation Integer param) {
    return param.toString();
  }
}