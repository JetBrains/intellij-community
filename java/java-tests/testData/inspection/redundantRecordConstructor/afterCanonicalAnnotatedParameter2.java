// "Convert canonical constructor to compact form" "true"
record Rec(@Anno int x, int y) {
    public Rec {
        if (x < 0) throw new IllegalArgumentException();
    }
}

@interface Anno {}