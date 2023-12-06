class DataflowChar {
    void test(char c) {
        if (c < 'a' || c > 'z') return;

        switch<caret>(c) {
            case 'b':break;
            case 'f':break;
            case 'f'+1:break;
            case 120:break;
        }
    }
}