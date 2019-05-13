class <symbolName descr="null">A</symbolName> {
    static void <symbolName descr="null">foo</symbolName>(){}
}
class <symbolName descr="null">B</symbolName> extends <symbolName descr="null">A</symbolName> {
    static {
        <symbolName descr="null">A</symbolName>.<symbolName descr="null" type="STATIC_METHOD">foo</symbolName>();
        <symbolName descr="null" type="STATIC_METHOD">foo</symbolName>();
    }
}