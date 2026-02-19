package org.example;

import org.jetbrains.annotations.NotNull;

public class ClassWithAnnotations {
    private static final Boolean SIMPLE_FIELD = false;


    @FieldAnnotation
    public static final Boolean ANOTHER_ANNOTATED_FIELD = false;


    @FieldAnnotation
    public static final Boolean ANNOTATED_FIELD = false;


    @FieldAnnotation
    private @NotNull Boolean annotatedField = false;

    private @NotNull Boolean secondAnnotatedField = false;

    @NotNull Boolean nonPrivateAnnotatedField = false;


    @NotNull
    @FieldAnnotation
    Boolean secondNonPrivateAnnotatedField = false;

    public Boolean simpleField = false;
}

interface

@interface FieldAnnotation {
}