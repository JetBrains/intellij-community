class Foo {
    void foo() {
        final IndentInfo indentProperty = Formatter.getInstance().getWhiteSpaceBefore(new PsiBasedFormattingModel(element.getContainingFile(),
                                                                                                                  settings),
                                                                                      CodeFormatterFacade.createBlock(element.getContainingFile(),
                                                                                                                      settings),
                                                                                      settings,
                                                                                      indentOptions,
                                                                                      child2.getTextRange(),
                                                                                      false);
    }
}