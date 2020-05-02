class Foo {
    private void bar() {
        String text = null;
        try {
            <selection>text = getString();</selection>
        }
        catch(Exception e) {
            System.out.println(text);
        }
    }
    private void getString() {
        return "hello";
    }
}