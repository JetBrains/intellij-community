class S {
    private String foo = <flown11>"xxx";

    {
        String bar = <flown1>foo;
        if (bar.equals("a")) {
        } else if (bar.equals("b")) {
        }

        if (bar.equals("c")) {
            bar = <flown2>"";
        }

        System.out.println("bar: " + <caret>bar);
    }
}