enum NewEnum   {

  FOO(1),
    BAR<caret>(3){
        public int getMyArg(){
            return myarg;
        }
    },
    BOO(2);

  int myarg;

  NewEnum(int myarg) {
    this.myarg = myarg;
  }
  public int getMyArg(){
    return myarg;
  }
}