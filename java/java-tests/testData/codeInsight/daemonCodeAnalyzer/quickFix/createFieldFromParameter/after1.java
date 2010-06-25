// "Create Field For Parameter 'id'" "true"

class Person {
    private String __fname;
    private final int myId;
    private String __lname;
    private String __street;
 
 
    public Person ( String i_lname, String i_fname, String i_street)
    {
        __lname = i_lname;
        __fname = i_fname;
        __street = i_street;
    }
 
    public Person ( int id<caret>, String i_lname, String i_fname, String i_street)
    {
        myId = id;
        __lname = i_lname;
        __fname = i_fname;
        __street = i_street;
    }

}