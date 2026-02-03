class Test {
    private B myField;

    public void method() {
        Object o = myField;
        String a = myField.bbb.xxx();
    }

    private Object f<caret>oo(){
        return myField;
    }

    private static class B {
        private C bbb;
    }

    private static class C {
        String xxx() {return "";}
    }

}