module M {
    exports pack1 to java.compiler;
    opens pack1.pack2 to java.base, java.xml;
}