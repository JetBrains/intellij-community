package org.jetbrains.annotations;
import org.intellij.lang.annotations.Language;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE, ElementType.TYPE, ElementType.PACKAGE})
@interface Nls { }

@Target({ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.PARAMETER})
@interface Anno {}

class Test {
    String test(@Anno @Language("HTML") @Nls String sample){
        sample = "<html>EOF</html>";
        return newMethod(sample);
    }

    @Language("HTML")
    @Nls
    private String newMethod(@Language("HTML") @Nls String sample) {
        return sample;
    }
}