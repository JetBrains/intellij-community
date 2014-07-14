class Test {
    byte b = 0;

    void bar() {
        if (b == 0) {
            b++;
            b += 0;
            //System.out.println(b + 10);
            System.out.println(b);
        }
    }
}