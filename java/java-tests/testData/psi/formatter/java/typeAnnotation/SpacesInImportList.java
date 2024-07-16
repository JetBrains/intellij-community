package org.example

import
  org   .   jetbrains .  annotations.   NotNull;
import    org.   jetbrains   .  annotations.
  Nullable;
import org   .   jetbrains .
  annotations .*;

public class Formatter {
    @NotNull
    String getNotNullName() {
        return "";
    }

    @Nullable
    String getNullableName() {
        return null;
    }
}