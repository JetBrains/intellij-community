package org.example;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Formatter {
    @NotNull
    @Nullable
    String breakLineBetweenAnnotations() {
        return "";
    }

    @Nullable
    @NotNull
    String breakLineBetweenTypeAndAnnotations() {
        return null;
    }

    @Nullable
    <T> String breakLineBetweenTypeParameterAndAnnotation() {
        return null;
    }

    @Nullable
    @NotNull
    <T> String breakLineMixed() {
        return null;
    }
}