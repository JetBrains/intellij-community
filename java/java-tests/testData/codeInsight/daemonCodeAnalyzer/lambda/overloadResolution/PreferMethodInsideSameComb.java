interface ToStringBug {

    static void toString(String s) {}

    class Inner implements ToStringBug {
       
        {
           toString<error descr="Expected no arguments but found 1">( "x")</error>;
        }
    }
}
