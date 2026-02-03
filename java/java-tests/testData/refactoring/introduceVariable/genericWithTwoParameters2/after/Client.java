class Client {
    void method() {
        Pair<String, Pair<Integer, Boolean>> p = new PairProvider<String, Integer, Boolean>().getPair();
    }
}