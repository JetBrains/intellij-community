// "Make 'Child' extend 'Parent'|->non-sealed" "true-preview"
sealed interface Parent permits Child, Foo {}

interface OtherParent {}

non-sealed interface Foo extends Parent {}

non-sealed interface Child extends OtherParent, Parent {}