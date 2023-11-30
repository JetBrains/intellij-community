package com.siyeh.igtest.bugs;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ReflectionForUnavailableAnnotationInspection {
    public void foo() throws NoSuchMethodException {
        getClass().getAnnotation(Retention.class);
        getClass().getAnnotation(UnretainedAnnotation.class);
        getClass().getAnnotation(SourceAnnotation.class);
        getClass().isAnnotationPresent(Retention.class);
        getClass().isAnnotationPresent(UnretainedAnnotation.class);
        getClass().isAnnotationPresent(SourceAnnotation.class);
        getClass().getMethod("foo").getAnnotation(SourceAnnotation.class);
    }
}
@interface UnretainedAnnotation {
}
@Retention(RetentionPolicy.SOURCE)
@interface SourceAnnotation {
}
