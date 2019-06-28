package pkg;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.ScheduledForRemoval(inVersion = "123.456")
public class AnnotatedClass {
  public static final String NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS = "";
  public String nonAnnotatedFieldInAnnotatedClass = "";

  public AnnotatedClass() {}

  public static void staticNonAnnotatedMethodInAnnotatedClass() {}

  public void nonAnnotatedMethodInAnnotatedClass() {}

  @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public static final String ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS = "";
  @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public String annotatedFieldInAnnotatedClass = "";

  @ApiStatus.ScheduledForRemoval(inVersion = "123.456")
  public AnnotatedClass(String s) {}

  @ApiStatus.ScheduledForRemoval(inVersion = "123.456")
  public static void staticAnnotatedMethodInAnnotatedClass() {}

  @ApiStatus.ScheduledForRemoval(inVersion = "123.456")
  public void annotatedMethodInAnnotatedClass() {}
}