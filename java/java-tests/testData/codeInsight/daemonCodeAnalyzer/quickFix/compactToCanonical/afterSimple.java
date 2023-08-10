// "Convert compact constructor to canonical" "true-preview"
record R(int x,int y) {
    /*
      hello
       */
    public R(int x, int y) {<caret>
        this.x = x;
        this.y = y;
    }
}
