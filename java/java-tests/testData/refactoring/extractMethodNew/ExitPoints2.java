class Test{
    public void foo() {
        if (cond1){
            <selection>if (cond2) {
                System.out.println();
                return;
            }</selection>
        }
        else if (cond3){
        }
        x();
    }
}
