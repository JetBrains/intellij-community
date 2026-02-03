package org.example;

import org.jspecify.annotations.Nullable;

import module org.jspecify;

public class Formatter {
    @NonNull @Nullable String breakLineBetweenAnnotations() {
        return "";
    }

    @Nullable @NonNull String breakLineBetweenTypeAndAnnotations() {
        return null;
    }

    @Nullable <T> String breakLineBetweenTypeParameterAndAnnotation() {
        return null;
    }

    @Nullable @NonNull <T> String breakLineMixed() {
        return null;
    }
}