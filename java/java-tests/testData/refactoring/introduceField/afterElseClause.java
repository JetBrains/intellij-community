class A {
    public static final String test2;

    public void demo(String val){
        if ("test1".equals(val)){
            //do somethign
        } else {
            test2 = "test2";
            if (test2.equals(val)) {
                //... something else
            }
        }
    }
}
