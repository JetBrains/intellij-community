
class InlineMethod {
    void test() {
        oth<caret>er();
    }

    InlineMethod other() {
        System.out.println("");
        return this;
    }
}
