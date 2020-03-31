class A {
    public String method() {
        try {
            <selection>try {
                return "";
            }
            finally {
                System.out.println("f");
            }</selection>
        }
        catch (Error e) {

        }
        return "";
    }
}