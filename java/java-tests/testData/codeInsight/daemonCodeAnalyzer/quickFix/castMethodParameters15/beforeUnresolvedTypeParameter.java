// "Cast parameter to 'M'" "false"
class a {
    void doSomething(String[] data) {}
    void test() {
        doSomething(Li<caret>st.of(""));
    }

    interface List<L> {
        static <M> List<M> of(M... ms) {
            return null;
        }
    }
}

