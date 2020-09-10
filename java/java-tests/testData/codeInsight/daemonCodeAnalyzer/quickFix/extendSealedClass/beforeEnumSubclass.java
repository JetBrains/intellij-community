// "Implement 'Parent'" "true"
sealed interface Parent permits MyEnum<caret> {}

enum MyEnum {}