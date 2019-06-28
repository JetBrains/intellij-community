package pkg;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.ScheduledForRemoval(inVersion = "123.456")
public enum AnnotatedEnum {
  NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM,
  @ApiStatus.ScheduledForRemoval(inVersion = "123.456") ANNOTATED_VALUE_IN_ANNOTATED_ENUM
}
