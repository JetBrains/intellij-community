enum NewEnum   {

    FOO(1),
    BOO(2)
    ,
    <caret>BAR(3){
        public int getMyArg(){
            return myarg;
        }
    };
    int myarg;

    NewEnum(int myarg) {
        this.myarg = myarg;
    }
    public int getMyArg(){
        return myarg;
    }
}