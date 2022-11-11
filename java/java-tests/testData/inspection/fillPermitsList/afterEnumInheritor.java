// "Add missing subclasses to the permits clause" "true-preview"

sealed interface Parent permits Foo {

}

enum Foo implements Parent {
  A {}
}
