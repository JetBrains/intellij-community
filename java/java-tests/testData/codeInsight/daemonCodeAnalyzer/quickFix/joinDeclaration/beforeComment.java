// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class Test {
    {
        String /*comment 1*/ s<caret>s /*comment 2*/ = "" /*comment 3*/; // comment 4
        // comment A
        /*comment B*/ ss /*comment C*/ = "hello" + /*comment D*/ " world" /*comment E*/; // comment F
    }
}