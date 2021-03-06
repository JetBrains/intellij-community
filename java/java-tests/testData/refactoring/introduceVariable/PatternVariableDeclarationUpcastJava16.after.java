class A {
    void test(String obj) {
        if (obj instanceof String) {
            var temp = obj;
            if (temp.trim().isEmpty()) {
                System.out.println("Found");
            }
        }
    }
}