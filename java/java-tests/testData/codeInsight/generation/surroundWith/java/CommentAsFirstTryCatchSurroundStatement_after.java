class Test {
    void foo() {
        try {
            // This is comment"
            int i = 1;
        } catch (Exception e) {
            <caret><selection>e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.</selection>
        }
    }
}