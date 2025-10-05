package org.example;

import module org.jetbrains.annotations;

public class Formatter {
    @NotNull
    @Nullable
    String breakLineBetweenAnnotations() {
        return "";
    }

    @Nullable
    @NotNull String breakLineBetweenTypeAndAnnotations() {
        return null;
    }

    @Nullable <T> String breakLineBetweenTypeParameterAndAnnotation() {
        return null;
    }

    @Nullable
    @NotNull
    <T> String breakLineMixed() {
      return null;
    }
}