// "non-sealed" "true"
sealed interface Parent permits Child<caret>, Foo {}

interface OtherParent {}

non-sealed interface Foo extends Parent {}

interface Child extends OtherParent {}