class Foo {}
class Bar extends Foo {}
class Goo extends Bar {}

class Params<X extends Foo> {}
class Main extends Params<Foo><caret>

