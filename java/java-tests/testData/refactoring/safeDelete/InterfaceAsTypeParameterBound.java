interface Foo1 {}
interface FooTo<caret>Delete extends Foo1 {}
class TypeParamOwner<B extends FooToDelete> {}