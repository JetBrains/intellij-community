class C {
    private int[] list;

    private Integer find(int id) {
        <selection>
        int n = 0;
        for (int n1 : list) {
            n = n1;
            if (n == id) {
                return n <= 0 ? null : n;
            }
        }
        </selection>
        throw new RuntimeException();
    }
}