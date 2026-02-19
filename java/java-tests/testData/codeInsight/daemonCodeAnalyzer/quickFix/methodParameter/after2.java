// "Make 'VarArgMismatch2' take parameter of type 'int[]' here" "true-preview"
record VarArgMismatch2(int[] x) {
  public VarArgMismatch2(int[] x) {
    this.x = x;
  }
}