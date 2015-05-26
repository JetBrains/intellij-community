class Test {
    public void foo(Object anObject) {
        bar(anObject.toStrin<caret>g());
    }
    void bar(String f){}
}