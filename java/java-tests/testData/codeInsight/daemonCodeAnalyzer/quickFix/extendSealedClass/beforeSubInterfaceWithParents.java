// "Make 'Child' extend 'Parent'|->non-sealed" "true-preview"
sealed interface Parent permits Child<caret>, Foo {}

interface OtherParent {}

non-sealed interface Foo extends Parent {}

interface Child extends OtherParent {}