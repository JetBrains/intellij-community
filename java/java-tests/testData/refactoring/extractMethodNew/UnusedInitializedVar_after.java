class Foo {
    private void bar() {
        String text = null;
        try {
            text = newMethod();
        }
        catch(Exception e) {
            System.out.println(text);
        }
    }

    private String newMethod() {
        String text;
        text = getString();
        return text;
    }

    private void getString() {
        return "hello";
    }
}