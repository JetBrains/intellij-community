// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class Test {
    {
        // comment A
        /*comment B*/ // comment 4
        /*comment C*/
        /*comment E*/
        String /*comment 1*/ ss /*comment 2*/   /*comment 3*/ = "hello" + /*comment D*/ " world"; // comment F
    }
}