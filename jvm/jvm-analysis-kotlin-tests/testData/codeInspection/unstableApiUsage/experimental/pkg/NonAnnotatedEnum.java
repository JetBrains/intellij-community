package pkg;

import org.jetbrains.annotations.ApiStatus;

public enum NonAnnotatedEnum {
  NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM,
  @ApiStatus.Experimental ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM
}
