package pkg;

import org.jetbrains.annotations.ApiStatus;

public class NonAnnotatedClass {
  public static final String NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS = "";
  public String nonAnnotatedFieldInNonAnnotatedClass = "";

  public NonAnnotatedClass() {}

  public static void staticNonAnnotatedMethodInNonAnnotatedClass() {}

  public void nonAnnotatedMethodInNonAnnotatedClass() {}

  @ApiStatus.Experimental public static final String ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS = "";
  @ApiStatus.Experimental public String annotatedFieldInNonAnnotatedClass = "";

  @ApiStatus.Experimental
  public NonAnnotatedClass(String s) {}

  @ApiStatus.Experimental
  public static void staticAnnotatedMethodInNonAnnotatedClass() {}

  @ApiStatus.Experimental
  public void annotatedMethodInNonAnnotatedClass() {}
}