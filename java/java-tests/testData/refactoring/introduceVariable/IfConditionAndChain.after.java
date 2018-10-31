class Test {
    void test(Object obj) {
        if (obj instanceof String) {
            String temp = (String) obj;
            if (!temp.isEmpty()) {
                System.out.println(temp.trim());
            }
        }
    }
}
