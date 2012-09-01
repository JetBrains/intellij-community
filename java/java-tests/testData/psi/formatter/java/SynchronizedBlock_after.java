class Foo {
    public void foo() {
        LOG.bar();
        synchronized(this){
            bar();
            bar();
        }
    }
}