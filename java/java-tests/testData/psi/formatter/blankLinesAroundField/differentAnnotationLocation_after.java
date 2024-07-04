package org.example;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassWithAnnotations {
    public final boolean simpleField1 = true;


    private @Nullable
    final Integer annotatedField1 = 10;
    public Boolean simpleField2 = false;


    public @NotNull Integer annotatedField2 = 1;
    private Boolean simpleField3 = false;


    @NotNull
    private Integer getAnnotatedField3 = 2;
}
