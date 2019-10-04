interface ToStringBug {

    static void toString(String s) {}

    class Inner implements ToStringBug {
       
        {
           toString<error descr="'toString()' in 'java.lang.Object' cannot be applied to '(java.lang.String)'">( "x")</error>;
        }
    }
}
