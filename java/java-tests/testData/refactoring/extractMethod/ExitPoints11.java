class C {
    private int[] list;

    private int find(int id) {
        <selection>for (int n : list) {
            if (n == id) {
                return n <= 0 ? 0 : n;
            }
        }</selection>
        throw new RuntimeException();
    }
}