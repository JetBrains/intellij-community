module M {
    exports my.api to M2, M4;
    exports <caret>my.api to M6;
    exports my.api;
}