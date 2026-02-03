import java.util.function.Consumer;
import java.util.function.Function;

class Test {
  private static  <T> T withProject(final Function<ProjectEnvironment, T> calculation) {
    System.out.println(calculation);
    return null;
  }

  private static <T> T <warning descr="Private method 'withProject(java.util.function.Consumer<ProjectEnvironment>)' is never used">withProject</warning>(final Consumer<ProjectEnvironment> calculation){
    System.out.println(calculation);
    return null;
  }

  public static String foo() {
    return withProject(ProjectEnvironment::getProject);
  }
}

class ProjectEnvironment {
  String getProject() {
    return null;
  }
}