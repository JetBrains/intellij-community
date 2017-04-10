module M {
    opens my.api;
    opens my.api to M6, M2, M4;
}