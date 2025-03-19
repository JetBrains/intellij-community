class DataflowChar {
    void test(char c) {
        if (c < 'a' || c > 'z') return;

        switch<caret>(c) {
            case 'a':
                break;
            case 'b':break;
            case 'c':
                break;
            case 'd':
                break;
            case 'e':
                break;
            case 'f':break;
            case 'f'+1:break;
            case 'h':
                break;
            case 'i':
                break;
            case 'j':
                break;
            case 'k':
                break;
            case 'l':
                break;
            case 'm':
                break;
            case 'n':
                break;
            case 'o':
                break;
            case 'p':
                break;
            case 'q':
                break;
            case 'r':
                break;
            case 's':
                break;
            case 't':
                break;
            case 'u':
                break;
            case 'v':
                break;
            case 'w':
                break;
            case 120:break;
            case 'y':
                break;
            case 'z':
                break;
        }
    }
}