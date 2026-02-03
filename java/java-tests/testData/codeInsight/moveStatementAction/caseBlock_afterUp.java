class Test {
    void m(int v) {
        switch (v) {
            <caret>case 2:
                System.out.println(2);
                break;
            case 1:
                System.out.println(1);
                break;
            case 3:
                System.out.println(3);
                break;
        }
    }
}