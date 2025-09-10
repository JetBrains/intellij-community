package org.example

import
  org   .   jspecify .  annotations.   NonNull;
import    org.   jspecify   .  annotations.
  Nullable;
import org   .   jspecify .
  annotations .*;

public class Formatter {
    @NonNull
    String getNotNullName() {
        return "";
    }

    @Nullable
    String getNullableName() {
        return null;
    }
}