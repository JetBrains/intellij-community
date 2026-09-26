package org.example;


import module org.jspecify;

public class Formatter {
    @NonNull @org.jspecify.annotations.Nullable String breakLineBetweenAnnotations() {
        return "";
    }

    @org.jspecify.annotations.Nullable @NonNull String breakLineBetweenTypeAndAnnotations() {
        return null;
    }

    @org.jspecify.annotations.Nullable <T> String breakLineBetweenTypeParameterAndAnnotation() {
        return null;
    }

    @org.jspecify.annotations.Nullable @NonNull <T> String breakLineMixed() {
        return null;
    }
}