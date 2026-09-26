// "Add constructor parameter" "true"
import java.util.Collections;
import java.util.List;

// IDEA-345876
abstract class Test {
  private final String taskName;

  private final String config;

  private final String <caret>moduleName;

  public Test(String config, String taskName, String taskLocation) {
    this(config, taskName, taskLocation, Collections.emptyList());
  }

  public Test(String config, String taskLocation,
              List<String> additionalArguments) {
    this(config, null, taskLocation, additionalArguments);
  }

  public Test(String config, String taskName, String taskLocation,
              List<String> additionalArguments) {
    this.config = config;
    this.taskName = taskName;
    System.out.println(additionalArguments);
  }
}
