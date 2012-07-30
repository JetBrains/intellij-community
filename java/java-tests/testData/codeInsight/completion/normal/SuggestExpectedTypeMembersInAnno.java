enum TestEnum { TEXT1, TEXT2 }
public @interface TestAnnotation { TestEnum value(); }

@TestAnnotation(Tex<caret>)