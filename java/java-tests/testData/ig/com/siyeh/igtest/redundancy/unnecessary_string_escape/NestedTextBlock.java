class NestedTextBlock {
  String s = """
    String s = \"<warning descr="'\\\\"' is unnecessarily escaped"><caret>\"</warning><warning descr="'\\\\"' is unnecessarily escaped">\"</warning>
        test
    \"<warning descr="'\\\\"' is unnecessarily escaped">\"</warning><warning descr="'\\\\"' is unnecessarily escaped">\"</warning>;
""";
}