@interface Anno { MyEnum value(); }
enum MyEnum { foo, bar }
@Anno(value=<caret>)