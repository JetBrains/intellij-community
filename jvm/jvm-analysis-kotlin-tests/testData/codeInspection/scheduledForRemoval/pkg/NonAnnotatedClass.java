package pkg;

import org.jetbrains.annotations.ApiStatus;

public class NonAnnotatedClass {
  public static final String NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS = "";
  public String nonAnnotatedFieldInNonAnnotatedClass = "";

  public NonAnnotatedClass() {}

  public static void staticNonAnnotatedMethodInNonAnnotatedClass() {}

  public void nonAnnotatedMethodInNonAnnotatedClass() {}

  @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public static final String ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS = "";
  @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public String annotatedFieldInNonAnnotatedClass = "";

  @ApiStatus.ScheduledForRemoval(inVersion = "123.456")
  public NonAnnotatedClass(String s) {}

  @ApiStatus.ScheduledForRemoval(inVersion = "123.456")
  public static void staticAnnotatedMethodInNonAnnotatedClass() {}

  @ApiStatus.ScheduledForRemoval(inVersion = "123.456")
  public void annotatedMethodInNonAnnotatedClass() {}
}