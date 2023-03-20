// "Make 'MyEnum' implement 'Parent'" "true-preview"
sealed interface Parent permits MyEnum<caret> {}

enum MyEnum {}