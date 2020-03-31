// "Convert canonical constructor to compact form" "true" 
record Rec(int x, int y) {
    public Rec {
        this.x = y;
        this.y = x;
    }
}