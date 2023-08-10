class X {
    void ff(String[] a) {
        if (a.length != 0)
            for (String arg : a)
                if (arg.length() > 1)
                    for (int i = 0; i < arg.length(); i++)<caret>
                        System.out.println(arg.charAt(i));
                else System.out.println(0);
        else System.out.println("no");
    }
}