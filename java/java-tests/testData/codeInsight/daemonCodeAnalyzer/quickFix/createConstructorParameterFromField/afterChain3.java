// "Add constructor parameter" "true"
import java.util.Collections;
import java.util.List;

// IDEA-345876
abstract class Test {
  private final String taskName;

  private final String config;

  private final String moduleName;

  public Test(String config, String taskName, String taskLocation, String moduleName) {
    this(config, taskName, taskLocation, Collections.emptyList(), moduleName);
  }

  public Test(String config, String taskLocation,
              List<String> additionalArguments, String moduleName) {
    this(config, null, taskLocation, additionalArguments, moduleName);
  }

  public Test(String config, String taskName, String taskLocation,
              List<String> additionalArguments, String moduleName) {
    this.config = config;
    this.taskName = taskName;
      this.moduleName = moduleName;
      System.out.println(additionalArguments);
  }
}
