class Test {
    public void foo() {
        bar(<caret>java.io.File.createTempFile("a", "b"));
    }
    void bar(java.io.File f){}
}