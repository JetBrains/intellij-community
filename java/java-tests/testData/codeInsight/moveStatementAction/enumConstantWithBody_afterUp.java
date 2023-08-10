enum NewEnum   {

    FOO(1),
    <caret>BAR(3){
        public int getMyArg(){
            return myarg;
        }
    }
    ,
    BOO(2);
    int myarg;

    NewEnum(int myarg) {
        this.myarg = myarg;
    }
    public int getMyArg(){
        return myarg;
    }
}