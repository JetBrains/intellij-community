class X {
    void test(Object obj) {
        if (<selection>obj instanceof (String s)</selection> && s.length() > 5) {
            System.out.println(s);
        }
    }
}