module M {
    exports my.api;
    exports <caret>my.api to M2, M4;
    exports my.api to M6;
}