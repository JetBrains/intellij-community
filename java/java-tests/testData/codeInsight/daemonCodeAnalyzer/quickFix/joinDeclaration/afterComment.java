// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class Test {
    {/*comment 1*//*comment 2*//*comment 3*/// comment 4
        // comment A
        /*comment B*/ /*comment C*/ /*comment E*/
        String ss = "hello" + /*comment D*/ " world"; // comment F
    }
}