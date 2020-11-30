module M {
    exports pack1 to java.compiler;
    exports pack3 to java.xml;
    exports pack4 to java.xml;
    exports pack1.pack2;

    opens pack1 to java.base;
    opens pack3 to java.compiler;
    opens pack4 to java.desktop;
    opens pack1.pack2 to java.base, java.xml;
}