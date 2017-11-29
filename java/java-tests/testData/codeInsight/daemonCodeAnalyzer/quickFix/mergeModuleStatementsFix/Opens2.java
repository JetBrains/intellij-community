module M {
    opens my.api;
    opens <caret>my.api to M2, M4;
    opens my.api to M6;
}