package org.example;

import org.jetbrains.annotations.NotNull;

public class ClassWithAnnotations {
    private static final Boolean SIMPLE_FIELD = false;


    @NotNull
    public static final Boolean ANOTHER_ANNOTATED_FIELD = false;


    @NotNull
    public static final Boolean ANNOTATED_FIELD = false;


    private @NotNull Boolean annotatedField = false;

    public Boolean simpleField = false;
}
