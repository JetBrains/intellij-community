class A{
    void foo(){
        try {
            f();
        } catch (Exception e) {
            <selection>throw new RuntimeException(e);</selection>
        }
    }
}