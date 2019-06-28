package pkg;

import org.jetbrains.annotations.ApiStatus;

public enum NonAnnotatedEnum {
  NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM,
  @ApiStatus.ScheduledForRemoval(inVersion = "123.456") ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM
}
