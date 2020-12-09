// "non-sealed" "true"
sealed interface Parent permits Child, Foo {}

interface OtherParent {}

non-sealed interface Foo extends Parent {}

non-sealed interface Child extends OtherParent, Parent {}