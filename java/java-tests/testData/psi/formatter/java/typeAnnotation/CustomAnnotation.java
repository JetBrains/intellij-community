package org.example;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

@Target({ElementType.TYPE_USE})
@interface CustomAnno {}

public class Formatter {
    @CustomAnno String getCustomString() {
        return null;
    }

    @CustomAnno <T, V> List<T> getCustomList() {
        return null;
    }
}