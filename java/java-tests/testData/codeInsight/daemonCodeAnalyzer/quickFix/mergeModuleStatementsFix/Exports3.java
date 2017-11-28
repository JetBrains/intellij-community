module M {
    // first
    exports my.api to M2;
    // second
    exports <caret>my.api to M6;
    // third
    exports my.api to M4;
}