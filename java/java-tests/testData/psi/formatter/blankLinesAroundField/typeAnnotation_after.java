package org.example;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassWithAnnotations {
    public final boolean simpleField1 = true;
    private @Nullable
    final Integer typeAnnotatedField1 = 10;
    public Boolean simpleField2 = false;
    public @NotNull Integer typeAnnotatedField2 = 1;
    @NotNull Boolean typeAnnotatedField3 = true;
    private Boolean simpleField3 = false;


    @NotNull
    private Integer typeAnnotatedField4 = 2;


    @FieldAnnotation
    private Boolean annotatedField4 = 4;


    @FieldAnnotation
    @NotNull
    private Boolean annotatedField5 = 4;


    @NotNull
    @FieldAnnotation
    private Boolean annotatedField6 = 4;


    @FieldAnnotation
    private @NotNull Boolean annotatedField7 = 4;
}

@interface FieldAnnotation {
}