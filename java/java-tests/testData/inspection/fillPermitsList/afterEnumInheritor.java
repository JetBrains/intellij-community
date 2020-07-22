// "Add missing inheritors to permits list" "true"

sealed interface Parent permits Foo {

}

enum Foo implements Parent {
  A {}
}
