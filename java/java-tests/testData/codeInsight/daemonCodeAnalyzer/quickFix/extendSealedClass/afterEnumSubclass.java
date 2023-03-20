// "Make 'MyEnum' implement 'Parent'" "true-preview"
sealed interface Parent permits MyEnum {}

enum MyEnum implements Parent {}