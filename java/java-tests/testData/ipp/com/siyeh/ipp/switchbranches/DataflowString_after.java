class DataflowString {
    void test(String s) {
        switch(s) {
            case "foo":
                System.out.println("1");break;
            case "bar":
                System.out.println("2");break;
            case "baz":
                System.out.println("3");break;
            default:
                throw new IllegalArgumentException(s);
        }
        System.out.println("Go!");
        switch<caret> (s) {
            case "bar":
                break;
            case "baz":
                break;
            case "foo":
                break;
        }
    }
}