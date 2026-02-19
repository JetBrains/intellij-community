enum TestEnum { MYVAL1, MYVAL2 }
public @interface TestAnnotation { TestEnum value(); }

@TestAnnotation(TestEnum.MYVAL1<caret>)