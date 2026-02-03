class K {
    public K foo() {
        return <error descr="Inconvertible types; cannot cast 'void' to 'K'">(K) bar()</error>;
    }

    private void bar() {
    }
}