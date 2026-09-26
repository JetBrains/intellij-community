class Test {
    private String test(int[] all) {
        for (int z : all) {
            <selection>if (z > 5) continue;
            if (z < 0) return "sample";</selection>
        }
        return "default";
    }
}