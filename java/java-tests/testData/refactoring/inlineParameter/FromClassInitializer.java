class X {
    {
        String str = "";
        foo(str, str.substring(0));
    }

    void foo(String str, String st<caret>r1) {

    }
}