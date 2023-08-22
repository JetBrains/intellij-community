class C {
    void m() {
        switch (0) {
            case 0:
                System.out.println(0);
                break;
            <selection><caret>case 1:</selection>
                System.out.println(1);
                break;
        }
    }
}