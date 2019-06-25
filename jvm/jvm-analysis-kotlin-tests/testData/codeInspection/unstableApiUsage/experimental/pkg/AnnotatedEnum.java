package pkg;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public enum AnnotatedEnum {
  NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM,
  @ApiStatus.Experimental ANNOTATED_VALUE_IN_ANNOTATED_ENUM
}
