class Test {
    void m(int v) {
        switch (v) {
<selection><caret>            case 2:
                System.out.println(2);
                break;
            case </selection>3:
                System.out.println(3);
                break;
            case 1:
                System.out.println(1);
                break;
            case 4:
                System.out.println(4);
                break;
        }
    }
}