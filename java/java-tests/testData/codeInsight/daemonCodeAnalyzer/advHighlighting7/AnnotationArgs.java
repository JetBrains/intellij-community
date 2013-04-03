@interface Foo {
  String value(); 
}

@Foo(<error descr="Attribute value must be constant">"myclass: " + Bar.class</error>)
class Bar {}

@Foo("myclass: Bazz")
class Bazz{}

@Foo("myclass:" + "Bazz1")
class Bazz1{}

@Foo(Const.CONST + Const.CONST)
class Bazz2{}

@Foo(<error descr="Incompatible types. Found: 'java.lang.Class<FooBar>', required: 'java.lang.String'">FooBar.class</error>)
class FooBar {}

class Const {
  public static final String CONST = "const";
}
