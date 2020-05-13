class Test {
    String s1 = getString();    
    String s;

    {
        if (s1 != null) {
            String temp = s1.trim();
            s = temp;
        } else {
            s = "";
        }
    }

    native String getString();
}
