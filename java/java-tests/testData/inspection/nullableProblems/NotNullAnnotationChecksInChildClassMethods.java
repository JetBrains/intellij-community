import org.jetbrains.annotations.*;

public class NotNullAnnotationChecksInChildClassMethods {

  private static class A {
    @NotNull public String getNormalizedName() {
      return "classA";
    }
  }

  private static class B extends A {

    @Override public String <warning descr="Not annotated method overrides method annotated with @NotNull">getNormalizedName</warning>() {
      return "classB";
    }
  }

  private static class C extends B {

    @Override public String <warning descr="Not annotated method overrides method annotated with @NotNull">getNormalizedName</warning>() {
      return "classC";
    }
  }
}