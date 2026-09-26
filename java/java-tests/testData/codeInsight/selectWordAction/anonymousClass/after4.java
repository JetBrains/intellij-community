class C {
    void m() {
        new Runnable() <selection>{
            <caret>public void run() {}
        }</selection>;
    }
}