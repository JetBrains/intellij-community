// "Move assignment to field declaration" "false"
record S(int... j ){
  S(int j){
    this.j <caret>= 0;
  }
}