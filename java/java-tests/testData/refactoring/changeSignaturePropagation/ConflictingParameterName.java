class CallChain {
    private void depth1() {
        depth2("My first parameter");
    }

    private void depth2(String param) {
        depth3();
    }

    private void dep<caret>th3() {
        System.out.println("hello there");
    }
}