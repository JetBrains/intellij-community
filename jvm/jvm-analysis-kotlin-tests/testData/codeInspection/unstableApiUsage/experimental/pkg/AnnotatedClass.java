package pkg;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public class AnnotatedClass {
  public static final String NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS = "";
  public String nonAnnotatedFieldInAnnotatedClass = "";

  public AnnotatedClass() {}

  public static void staticNonAnnotatedMethodInAnnotatedClass() {}

  public void nonAnnotatedMethodInAnnotatedClass() {}

  @ApiStatus.Experimental public static final String ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS = "";
  @ApiStatus.Experimental public String annotatedFieldInAnnotatedClass = "";

  @ApiStatus.Experimental
  public AnnotatedClass(String s) {}

  @ApiStatus.Experimental
  public static void staticAnnotatedMethodInAnnotatedClass() {}

  @ApiStatus.Experimental
  public void annotatedMethodInAnnotatedClass() {}
}