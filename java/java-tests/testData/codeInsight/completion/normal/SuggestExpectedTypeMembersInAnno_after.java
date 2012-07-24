enum TestEnum { TEXT1, TEXT2 }
public @interface TestAnnotation { TestEnum value(); }

@TestAnnotation(TestEnum.TEXT1<caret>)