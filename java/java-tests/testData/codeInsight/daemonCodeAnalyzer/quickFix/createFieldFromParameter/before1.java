// "Create field for parameter 'id'" "true-preview"

class Person {
    private String __fname, __lname, __street;
 
 
    public Person ( String i_lname, String i_fname, String i_street)
    {
        __lname = i_lname;
        __fname = i_fname;
        __street = i_street;
    }
 
    public Person ( int id<caret>, String i_lname, String i_fname, String i_street)
    {
        __lname = i_lname;
        __fname = i_fname;
        __street = i_street;
    }

}