enum TestEnum { MYVAL1, MYVAL2 }
public @interface TestAnnotation { TestEnum value(); }

@TestAnnotation(MYVAL<caret>)