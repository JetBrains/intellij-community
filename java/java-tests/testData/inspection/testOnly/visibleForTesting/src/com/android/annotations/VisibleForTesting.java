package com.android.annotations;

public @interface VisibleForTesting {
  enum Visibility {
    /** The element should be considered protected. */
    PROTECTED,
    /** The element should be considered package-private. */
    PACKAGE,
    /** The element should be considered private. */
    PRIVATE
  }

  Visibility visibility() default Visibility.PRIVATE;
}
