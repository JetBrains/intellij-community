class A {
    public static final String string;

    public void demo(String val){
        if ("test1".equals(val)){
            //do somethign
        } else {
            string = "test2";
            if (string.equals(val)) {
                //... something else
            }
        }
    }
}
